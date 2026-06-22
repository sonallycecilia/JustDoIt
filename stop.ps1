# stop.ps1 — Para todos os processos do JustDoIt

Write-Host "==> Parando servicos Spring Boot..." -ForegroundColor Cyan
@(8080, 8081, 8082, 8083, 3000) | ForEach-Object {
    $port = $_
    $pids = (Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue).OwningProcess | Select-Object -Unique
    foreach ($p in $pids) {
        if ($p -and $p -ne 0) {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
            Write-Host "  Porta $port encerrada (PID $p)"
        }
    }
}

Write-Host "==> Parando containers Docker..." -ForegroundColor Cyan
$backend = Join-Path $PSScriptRoot "justdoit-backend"
& docker-compose -f "$backend\infra\docker-compose.yml" stop

Write-Host "Tudo parado." -ForegroundColor Green
