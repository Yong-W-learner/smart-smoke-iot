$ErrorActionPreference = 'Stop'
$deployDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$repoDir = Split-Path -Parent $deployDir
$envFile = Join-Path $deployDir '.env.local'

if (-not (Test-Path -LiteralPath $envFile)) {
    throw "缺少 $envFile，请先从 .env.example 复制并填写配置。"
}

foreach ($line in Get-Content -LiteralPath $envFile) {
    $trimmed = $line.Trim()
    if (-not $trimmed -or $trimmed.StartsWith('#')) { continue }
    $parts = $trimmed.Split('=', 2)
    if ($parts.Count -eq 2) {
        [Environment]::SetEnvironmentVariable($parts[0].Trim(), $parts[1], 'Process')
    }
}

& (Join-Path $repoDir 'demo\mvnw.cmd') spring-boot:run
