param(
    [string]$ComposeFile = "docker-compose.yml",
    [string]$EnvFile = ".env",
    [string]$ProjectName = ""
)

Get-Content -LiteralPath $EnvFile | Where-Object { $_ -match '^[A-Z0-9_]+=' } | ForEach-Object {
    $name, $value = $_ -split '=', 2
    Set-Item -Path "Env:$name" -Value $value
}

$databases = @(
    @{ Name = "conversation_db"; User = "conversation_user"; Password = $env:CONVERSATION_DB_PASSWORD },
    @{ Name = "orchestration_db"; User = "orchestration_user"; Password = $env:ORCHESTRATION_DB_PASSWORD },
    @{ Name = "knowledge_db"; User = "knowledge_user"; Password = $env:KNOWLEDGE_DB_PASSWORD },
    @{ Name = "evaluation_db"; User = "evaluation_user"; Password = $env:EVALUATION_DB_PASSWORD },
    @{ Name = "insight_db"; User = "insight_user"; Password = $env:INSIGHT_DB_PASSWORD }
)

$allowed = 0
$denied = 0
foreach ($source in $databases) {
    foreach ($target in $databases) {
        $compose = @("compose")
        if ($ProjectName) { $compose += @("-p", $ProjectName) }
        $compose += @("-f", $ComposeFile, "--env-file", $EnvFile, "exec", "-T", "postgres", "bash", "-lc")
        # Cross-database connections are expected to fail; preserve the exit code without promoting stderr to a PowerShell exception.
        $previousErrorActionPreference = $ErrorActionPreference
        $ErrorActionPreference = "Continue"
        $result = & docker @compose "PGPASSWORD='$($source.Password)' psql -h localhost -U '$($source.User)' -d '$($target.Name)' -Atc 'SELECT 1'" 2>$null
        $ErrorActionPreference = $previousErrorActionPreference
        if ($source.Name -eq $target.Name) {
            if ($LASTEXITCODE -ne 0 -or $result.Trim() -ne '1') { throw "所属数据库连接失败: $($source.User) -> $($target.Name)" }
            $allowed++
        } else {
            if ($LASTEXITCODE -eq 0) { throw "跨服务数据库连接未被拒绝: $($source.User) -> $($target.Name)" }
            $denied++
        }
    }
}
Write-Output "数据库隔离验证通过：允许 $allowed，拒绝 $denied"
