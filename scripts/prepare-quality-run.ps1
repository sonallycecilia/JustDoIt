param(
    [string]$Commit = $(if ($env:QUALITY_COMMIT) { $env:QUALITY_COMMIT } else { git rev-parse HEAD }),
    [string]$RunId = $(if ($env:QUALITY_RUN_ID) { $env:QUALITY_RUN_ID } else { [guid]::NewGuid().ToString() }),
    [string]$Environment = $(if ($env:QUALITY_ENVIRONMENT) { $env:QUALITY_ENVIRONMENT } else { "Local / $([System.Environment]::OSVersion.VersionString)" })
)

$ErrorActionPreference = 'Stop'
$repositoryRoot = [System.IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$evidenceDir = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'quality-reports'))
$testLog = [System.IO.Path]::GetFullPath((Join-Path $repositoryRoot 'quality-test.log'))

if (-not $evidenceDir.StartsWith($repositoryRoot, [System.StringComparison]::OrdinalIgnoreCase)) {
    throw "Diretório de evidências fora do repositório: $evidenceDir"
}

if (Test-Path -LiteralPath $evidenceDir) {
    Remove-Item -LiteralPath $evidenceDir -Recurse -Force
}
if (Test-Path -LiteralPath $testLog) {
    Remove-Item -LiteralPath $testLog -Force
}
New-Item -ItemType Directory -Force -Path $evidenceDir | Out-Null

$context = [ordered]@{
    schemaVersion = 1
    runId = $RunId
    commit = $Commit.Trim()
    worktreeDirty = [bool]((git status --porcelain | Out-String).Trim())
    startedAt = [DateTime]::UtcNow.ToString('o')
    environment = $Environment
}
$context | ConvertTo-Json -Depth 4 | Set-Content -Encoding utf8 (Join-Path $evidenceDir 'run-context.json')
Write-Host "[QUALIDADE] execução=$RunId commit=$($context.commit)"
