# start.ps1 — Sobe toda a stack do JustDoIt localmente
# Executar na raiz do projeto (onde ficam justdoit-backend/ e justdoit-frontend/)

$root    = $PSScriptRoot
$backend = Join-Path $root "justdoit-backend"
$frontend = Join-Path $root "justdoit-frontend"

function Open-Tab($title, $command, $workdir) {
    Start-Process powershell -ArgumentList "-NoExit", "-Command", "cd '$workdir'; $command" -WindowStyle Normal
}

# ── 1. Docker (MySQL + Redis) ────────────────────────────────────────────────
Write-Host "==> [1/3] Verificando MySQL e Redis..." -ForegroundColor Cyan

$mysqlRunning = docker ps --filter "name=db_justdoit" --filter "status=running" -q
if (-not $mysqlRunning) {
    Write-Host "     Subindo containers..." -ForegroundColor Yellow
    & docker-compose -f "$backend\infra\docker-compose.yml" up -d
    Write-Host "     Aguardando MySQL inicializar (15s)..."
    Start-Sleep -Seconds 15
} else {
    Write-Host "     Ja rodando." -ForegroundColor Green
}

# ── 2. Backend — 4 servicos em janelas separadas ─────────────────────────────
Write-Host "==> [2/3] Iniciando servicos Spring Boot..." -ForegroundColor Cyan

$services = @(
    @{ Name = "auth-service";         Module = ":services:auth-service:bootRun"         },
    @{ Name = "task-service";         Module = ":services:task-service:bootRun"         },
    @{ Name = "schedule-service";     Module = ":services:schedule-service:bootRun"     },
    @{ Name = "notification-service"; Module = ":services:notification-service:bootRun" }
)

foreach ($svc in $services) {
    Write-Host "     -> $($svc.Name)"
    Open-Tab $svc.Name ".\gradlew.bat $($svc.Module)" $backend
    Start-Sleep -Seconds 1
}

# ── 3. Frontend ───────────────────────────────────────────────────────────────
Write-Host "==> [3/3] Iniciando frontend..." -ForegroundColor Cyan
Open-Tab "frontend" "npx serve . --listen 3000" $frontend

# ── Resultado ─────────────────────────────────────────────────────────────────
Write-Host @"

=============================================================================
Stack iniciada! Aguarde os servicos subirem (~15s cada) e acesse:

  Cadastro : http://localhost:3000/pages/auth/signup.html
  Login    : http://localhost:3000/pages/auth/login.html
  Dashboard: http://localhost:3000/pages/dashboard/dashboard.html

Para parar tudo: .\stop.ps1
=============================================================================
"@ -ForegroundColor Green
