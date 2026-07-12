param(
    [string]$ComposeFile = "deploy/docker-compose.yml",
    [string]$EnvFile = "deploy/.env"
)

$root = Split-Path -Parent $PSScriptRoot
Get-Content -LiteralPath $EnvFile | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}

$deadline = (Get-Date).AddSeconds(90)
do {
    $postgres = docker compose -f $ComposeFile --env-file $EnvFile ps --format json postgres | ConvertFrom-Json
    if ($postgres.Health -eq "healthy") { break }
    Start-Sleep -Seconds 2
} while ((Get-Date) -lt $deadline)
if ($postgres.Health -ne "healthy") { throw "PostgreSQL 未在 90 秒内达到 healthy" }

$services = @(
    @{ Name = "conversation-service"; Database = "conversation_db"; Prefix = "CONVERSATION"; Port = 18181 },
    @{ Name = "ai-orchestration-service"; Database = "orchestration_db"; Prefix = "ORCHESTRATION"; Port = 18182 },
    @{ Name = "knowledge-service"; Database = "knowledge_db"; Prefix = "KNOWLEDGE"; Port = 18183 },
    @{ Name = "evaluation-service"; Database = "evaluation_db"; Prefix = "EVALUATION"; Port = 18184 },
    @{ Name = "insight-service"; Database = "insight_db"; Prefix = "INSIGHT"; Port = 18185 }
)

$processes = @()
try {
    foreach ($service in $services) {
        $user = $service.Prefix.ToLower() + "_user"
        $password = (Get-Item "Env:$($service.Prefix)_DB_PASSWORD").Value
        $env:SPRING_PROFILES_ACTIVE = "database"
        Set-Item "Env:$($service.Prefix)_DB_URL" "jdbc:postgresql://localhost:15432/$($service.Database)"
        Set-Item "Env:$($service.Prefix)_DB_USERNAME" $user
        Set-Item "Env:$($service.Prefix)_DB_PASSWORD" $password
        $env:SERVICE_JWT_SECRET = $env:SERVICE_JWT_SECRET

        $directory = Join-Path $root ("services\" + $service.Name)
        $jar = Join-Path $directory ("target\" + $service.Name + "-0.0.1-SNAPSHOT.jar")
        if (!(Test-Path $jar)) { throw "缺少可执行 JAR：$jar" }
        $log = Join-Path $env:TEMP ("p1-" + $service.Name + "-database.log")
        Remove-Item -LiteralPath $log -Force -ErrorAction SilentlyContinue
        $process = Start-Process java.exe -ArgumentList "-jar", $jar, "--server.port=$($service.Port)" -WorkingDirectory $directory -RedirectStandardOutput $log -RedirectStandardError ($log + ".err") -WindowStyle Hidden -PassThru
        $processes += $process
        $deadline = (Get-Date).AddSeconds(45)
        do {
            try { $health = Invoke-RestMethod "http://127.0.0.1:$($service.Port)/api/v1/health" -TimeoutSec 2 } catch { $health = $null }
            if ($null -ne $health) { break }
            Start-Sleep -Seconds 1
        } while ((Get-Date) -lt $deadline)
        if ($null -eq $health -or $health.code -ne 200 -or $health.data.status -ne "UP") {
            Get-Content -LiteralPath $log -Tail 80 -ErrorAction SilentlyContinue
            Get-Content -LiteralPath ($log + ".err") -Tail 80 -ErrorAction SilentlyContinue
            throw "database profile 健康检查失败：$($service.Name)"
        }
        $probe = docker compose -f $ComposeFile --env-file $EnvFile exec -T postgres psql -U postgres -d $service.Database -Atc "SELECT to_regclass('public.service_health_probe') IS NOT NULL" 2>$null
        if ($LASTEXITCODE -ne 0 -or $probe.Trim() -ne "t") { throw "Flyway 基线迁移缺失：$($service.Database)" }
        Write-Output "$($service.Name): database profile 与 Flyway 通过"
        Stop-Process -Id $process.Id -Force
    }
} finally {
    foreach ($process in $processes) { if (!$process.HasExited) { Stop-Process -Id $process.Id -Force -ErrorAction SilentlyContinue } }
}
