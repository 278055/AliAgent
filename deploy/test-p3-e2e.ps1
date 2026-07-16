[CmdletBinding()]
param(
    [switch]$KeepEnvironment
)

$ErrorActionPreference = "Stop"
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass -Force
$root = Split-Path -Parent $PSScriptRoot
$composeFile = Join-Path $PSScriptRoot "docker-compose.yml"
$envFile = Join-Path $PSScriptRoot ".p3-e2e.env"
$projectName = "aliagentp3e2e"
$knowledgePort = 18183
$legacyPort = 18080
$testTenant = "rag-test-p3-e2e"
$knowledgeProcess = $null
$legacyProcess = $null
$databaseToolDirectory = Join-Path $PSScriptRoot "p3-e2e\target"

function Invoke-Compose([string[]]$Arguments) {
    & docker compose -p $projectName -f $composeFile --env-file $envFile @Arguments
    if ($LASTEXITCODE -ne 0) { throw "Docker Compose failed: $Arguments" }
}

function Wait-Healthy([string[]]$Services) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        $states = @(Invoke-Compose @("ps", "--format", "json") | ConvertFrom-Json)
        $unhealthy = @($Services | Where-Object { ($states | Where-Object Service -eq $_).Health -ne "healthy" })
        if ($unhealthy.Count -eq 0) { return }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "Services did not become healthy: $Services"
}

function New-ServiceJwt([string]$Scope) {
    $now = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $header = '{"alg":"HS256","typ":"JWT"}'
    $payload = @{ sub = "gateway-service"; caller = "gateway-service"; aud = @("knowledge-service"); scopes = @($Scope); iat = $now; exp = $now + 300; jti = [guid]::NewGuid().ToString() } | ConvertTo-Json -Compress
    function ConvertTo-Base64Url([byte[]]$Bytes) { [Convert]::ToBase64String($Bytes).TrimEnd("=").Replace("+", "-").Replace("/", "_") }
    $encodedHeader = ConvertTo-Base64Url([Text.Encoding]::UTF8.GetBytes($header))
    $encodedPayload = ConvertTo-Base64Url([Text.Encoding]::UTF8.GetBytes($payload))
    $hmac = [Security.Cryptography.HMACSHA256]::new([Text.Encoding]::UTF8.GetBytes("test-p3-e2e-service-jwt-secret-at-least-32-bytes"))
    "$encodedHeader.$encodedPayload.$(ConvertTo-Base64Url($hmac.ComputeHash([Text.Encoding]::UTF8.GetBytes("$encodedHeader.$encodedPayload"))))"
}

function New-TrustedHeaders([string]$Scope, [string]$Snapshot) {
    @{
        "X-Service-Authorization" = "Bearer $(New-ServiceJwt $Scope)"
        "X-Tenant-Id" = $testTenant
        "X-Subject-Id" = "rag-test-subject"
        "X-Subject-Type" = "STAFF"
        "X-User-Roles" = "KNOWLEDGE_EDITOR"
        "X-User-Permissions" = "knowledge:write"
        "X-Authorization-Snapshot-Id" = $Snapshot
        "X-Trace-Id" = "rag-test-p3-e2e-trace"
    }
}

function Wait-Http([string]$Uri, [hashtable]$Headers = @{}) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        try {
            $response = Invoke-WebRequest -Uri $Uri -Headers $Headers -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return $response }
        } catch { }
        Start-Sleep -Seconds 2
    } while ((Get-Date) -lt $deadline)
    throw "HTTP endpoint did not become ready: $Uri"
}

function Start-KnowledgeService {
    $environment = @{
        SPRING_PROFILES_ACTIVE = "database,mock"
        KNOWLEDGE_DB_URL = "jdbc:postgresql://localhost:15432/knowledge_db"
        KNOWLEDGE_DB_USERNAME = "knowledge_user"
        KNOWLEDGE_DB_PASSWORD = "test-p3-e2e-knowledge"
        RABBITMQ_HOST = "localhost"
        RABBITMQ_PORT = "15692"
        RABBITMQ_DEFAULT_USER = "aliagent"
        RABBITMQ_DEFAULT_PASS = "test-p3-e2e-rabbit"
        MINIO_ENDPOINT = "http://localhost:19000"
        MINIO_ROOT_USER = "aliagent"
        MINIO_ROOT_PASSWORD = "test-p3-e2e-minio"
        SERVICE_JWT_SECRET = "test-p3-e2e-service-jwt-secret-at-least-32-bytes"
    }
    foreach ($entry in $environment.GetEnumerator()) { Set-Item "Env:$($entry.Key)" $entry.Value }
    $log = Join-Path $env:TEMP "p3-e2e-knowledge.log"
    Remove-Item $log, ($log + ".err") -Force -ErrorAction SilentlyContinue
    $jar = Join-Path $root "services\knowledge-service\target\knowledge-service-0.0.1-SNAPSHOT.jar"
    $script:knowledgeProcess = Start-Process java.exe -ArgumentList "-jar", $jar, "--server.port=$knowledgePort" -WorkingDirectory $root -RedirectStandardOutput $log -RedirectStandardError ($log + ".err") -WindowStyle Hidden -PassThru
    Wait-Http "http://127.0.0.1:$knowledgePort/api/v1/health" (New-TrustedHeaders "GET:/api/v1/health" ([guid]::NewGuid().ToString())) | Out-Null
}

function Start-LegacyProbe([bool]$RemoteRead) {
    if ($script:legacyProcess -and !$script:legacyProcess.HasExited) { Stop-Process -Id $script:legacyProcess.Id -Force }
    $env:FEATURE_KNOWLEDGE_REMOTE_READ = $RemoteRead.ToString().ToLowerInvariant()
    $env:FEATURE_KNOWLEDGE_REMOTE_READ_TENANTS = $testTenant
    $env:FEATURE_KNOWLEDGE_REMOTE_READ_DUAL_RUN = "false"
    $env:KNOWLEDGE_SERVICE_BASE_URL = "http://localhost:$knowledgePort"
    $log = Join-Path $env:TEMP "p3-e2e-legacy.log"
    Remove-Item $log, ($log + ".err") -Force -ErrorAction SilentlyContinue
    $jar = Join-Path $root "legacy-monolith\target\legacy-monolith-compat-0.0.1-SNAPSHOT.jar"
    $script:legacyProcess = Start-Process java.exe -ArgumentList "-jar", $jar, "--server.port=$legacyPort" -WorkingDirectory $root -RedirectStandardOutput $log -RedirectStandardError ($log + ".err") -WindowStyle Hidden -PassThru
    Wait-Http "http://127.0.0.1:$legacyPort/p3-probe/rag/query?q=policy" | Out-Null
}

function Upload-Document([string]$Name, [string]$Content) {
    $path = Join-Path $env:TEMP $Name
    [IO.File]::WriteAllText($path, $Content, [Text.Encoding]::UTF8)
    try {
        $headers = New-TrustedHeaders "POST:/api/v1/knowledge/documents" ([guid]::NewGuid().ToString())
        $arguments = @("-sS", "-X", "POST", "http://127.0.0.1:$knowledgePort/api/v1/knowledge/documents")
        foreach ($entry in $headers.GetEnumerator()) { $arguments += @("-H", "$($entry.Key): $($entry.Value)") }
        $arguments += @("-F", "file=@$path;type=text/plain", "-w", "`nHTTP=%{http_code}")
        $result = & curl.exe @arguments
        if ($LASTEXITCODE -ne 0) { throw "curl upload failed" }
        $status = [int](($result[-1] -split "=", 2)[1])
        if ($status -ne 202) { throw "Expected upload 202, got $status" }
        return (($result[0..($result.Length - 2)] -join "" | ConvertFrom-Json).data)
    } finally { Remove-Item $path -Force -ErrorAction SilentlyContinue }
}

function Get-Task([string]$TaskId) {
    $headers = New-TrustedHeaders "GET:/api/v1/knowledge/ingestion-tasks/$TaskId" ([guid]::NewGuid().ToString())
    ((Invoke-WebRequest -Uri "http://127.0.0.1:$knowledgePort/api/v1/knowledge/ingestion-tasks/$TaskId" -Headers $headers -UseBasicParsing).Content | ConvertFrom-Json).data
}

function Wait-TaskState([string]$TaskId, [string]$Expected) {
    $deadline = (Get-Date).AddSeconds(90)
    do {
        Start-Sleep -Seconds 2
        $task = Get-Task $TaskId
        if ($task.state -eq $Expected) { return $task }
    } while ((Get-Date) -lt $deadline)
    throw "Task $TaskId did not reach $Expected"
}

function Invoke-DatabaseTool([string]$Action) {
    $pgJar = Get-ChildItem "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql\*\postgresql-*.jar" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    $vectorJar = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\pgvector\pgvector\*\pgvector-*.jar" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    & java.exe -cp "$databaseToolDirectory;$pgJar;$vectorJar" P3E2eDatabase $Action "test-p3-e2e-knowledge"
    if ($LASTEXITCODE -ne 0) { throw "Database tool failed: $Action" }
}

try {
    @(
        "MYSQL_ROOT_PASSWORD=test-p3-e2e-mysql-root",
        "MYSQL_MALL_PASSWORD=test-p3-e2e-mall",
        "POSTGRES_SUPERUSER_PASSWORD=test-p3-e2e-postgres",
        "CONVERSATION_DB_PASSWORD=test-p3-e2e-conversation",
        "ORCHESTRATION_DB_PASSWORD=test-p3-e2e-orchestration",
        "KNOWLEDGE_DB_PASSWORD=test-p3-e2e-knowledge",
        "EVALUATION_DB_PASSWORD=test-p3-e2e-evaluation",
        "INSIGHT_DB_PASSWORD=test-p3-e2e-insight",
        "REDIS_PASSWORD=test-p3-e2e-redis",
        "RABBITMQ_DEFAULT_USER=aliagent",
        "RABBITMQ_DEFAULT_PASS=test-p3-e2e-rabbit",
        "NACOS_AUTH_TOKEN=dGVzdC1wMy1lMmUtbmFjb3MtdG9rZW4tZm9yLWF1dG9tYXRlZC10ZXN0aW5n",
        "MINIO_ROOT_USER=aliagent",
        "MINIO_ROOT_PASSWORD=test-p3-e2e-minio"
    ) | Set-Content -LiteralPath $envFile -Encoding ascii

    Invoke-Compose @("up", "-d", "postgres", "rabbitmq", "minio")
    Wait-Healthy @("postgres", "rabbitmq", "minio")
    & "$PSScriptRoot\verify-database-isolation.ps1" -ComposeFile $composeFile -EnvFile $envFile -ProjectName $projectName
    if ($LASTEXITCODE -ne 0) { throw "Database isolation verification failed" }

    & mvn.cmd -pl services/knowledge-service -am package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Knowledge service package failed" }
    & mvn.cmd -f legacy-monolith/pom.xml package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Legacy probe package failed" }

    New-Item -ItemType Directory -Force -Path $databaseToolDirectory | Out-Null
    $pgJar = Get-ChildItem "$env:USERPROFILE\.m2\repository\org\postgresql\postgresql\*\postgresql-*.jar" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    $vectorJar = Get-ChildItem "$env:USERPROFILE\.m2\repository\com\pgvector\pgvector\*\pgvector-*.jar" | Sort-Object FullName -Descending | Select-Object -First 1 -ExpandProperty FullName
    & javac.exe -encoding UTF-8 -cp "$pgJar;$vectorJar" "$PSScriptRoot\p3-e2e\P3E2eDatabase.java" -d $databaseToolDirectory
    if ($LASTEXITCODE -ne 0) { throw "Database tool compilation failed" }

    Start-KnowledgeService

    Invoke-Compose @("stop", "rabbitmq")
    $rabbitTask = Upload-Document "rag-test-p3-e2e-rabbit.txt" "RabbitMQ fault injection payload."
    Start-Sleep -Seconds 7
    if ((Get-Task $rabbitTask.taskId).state -ne "PENDING") { throw "RabbitMQ outage must leave task pending" }
    Invoke-Compose @("start", "rabbitmq")
    Wait-Healthy @("rabbitmq")
    if ((Wait-TaskState $rabbitTask.taskId "SUCCEEDED").failure_diagnostic) { throw "Successful task has a diagnostic" }

    Invoke-Compose @("stop", "rabbitmq")
    $minioTask = Upload-Document "rag-test-p3-e2e-minio.txt" "MinIO fault injection payload."
    Invoke-Compose @("stop", "minio")
    Invoke-Compose @("start", "rabbitmq")
    Wait-Healthy @("rabbitmq")
    $failedTask = Wait-TaskState $minioTask.taskId "FAILED"
    if ($failedTask.failure_diagnostic -ne "INGESTION_SOURCE_UNAVAILABLE") { throw "MinIO diagnostic is not controlled" }
    Invoke-Compose @("start", "minio")
    Wait-Healthy @("minio")

    $setup = Invoke-DatabaseTool "setup"
    $snapshot = ([regex]::Match(($setup -join " "), "snapshot=([0-9a-f-]+)")).Groups[1].Value
    if (!$snapshot) { throw "Database setup did not return an authorization snapshot" }
    $headers = New-TrustedHeaders "POST:/api/v1/knowledge/retrieval:query" $snapshot
    $remote = Invoke-WebRequest -Uri "http://127.0.0.1:$knowledgePort/api/v1/knowledge/retrieval:query" -Method POST -Headers $headers -ContentType "application/json" -Body '{"query":"policy","topK":5}' -UseBasicParsing
    if ($remote.Content -notmatch "Remote policy result from knowledge service") { throw "Knowledge service retrieval did not return the published chunk" }

    Start-LegacyProbe $true
    $remoteLegacy = (Invoke-WebRequest -Uri "http://127.0.0.1:$legacyPort/p3-probe/rag/query?q=policy" -Headers $headers -UseBasicParsing).Content
    if ($remoteLegacy -notmatch "Remote policy result from knowledge service") { throw "Legacy remote read did not return knowledge-service data" }
    Stop-Process -Id $knowledgeProcess.Id -Force
    $knowledgeProcess = $null
    $unavailableLegacy = (Invoke-WebRequest -Uri "http://127.0.0.1:$legacyPort/p3-probe/rag/query?q=policy" -Headers $headers -UseBasicParsing).Content
    if ($unavailableLegacy -notmatch "local fallback result") { throw "Legacy remote outage did not fall back locally" }
    Start-LegacyProbe $false
    $disabledLegacy = (Invoke-WebRequest -Uri "http://127.0.0.1:$legacyPort/p3-probe/rag/query?q=policy" -UseBasicParsing).Content
    if ($disabledLegacy -notmatch "local fallback result") { throw "Disabled remote read did not use local RAG" }
    Write-Output "P3 E2E passed: isolation, RabbitMQ recovery, MinIO failure, remote read, and fallback"
} finally {
    if ($knowledgeProcess -and !$knowledgeProcess.HasExited) { Stop-Process -Id $knowledgeProcess.Id -Force -ErrorAction SilentlyContinue }
    if ($legacyProcess -and !$legacyProcess.HasExited) { Stop-Process -Id $legacyProcess.Id -Force -ErrorAction SilentlyContinue }
    if (Test-Path $databaseToolDirectory) { try { Invoke-DatabaseTool "cleanup"; Invoke-DatabaseTool "assert-clean" } catch { Write-Error $_ } }
    Remove-Item $databaseToolDirectory -Recurse -Force -ErrorAction SilentlyContinue
    if (!$KeepEnvironment) { Invoke-Compose @("down", "-v") }
    Remove-Item $envFile -Force -ErrorAction SilentlyContinue
}
