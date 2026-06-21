# deploy.ps1 — Build e envio para a VPS
# Executar de dentro de justdoit-backend/
# Uso: .\deploy.ps1
# Use -SkipBuild para pular o build (ex: .\deploy.ps1 -SkipBuild)

param([switch]$SkipBuild)

$Key  = ".claude\vps\justdoit.key"
$VPS  = "ubuntu@147.15.84.48"
$Dst  = "/opt/justdoit"

$Services = @(
    @{ Name = "auth";         Jar = "services/auth-service/build/libs"         },
    @{ Name = "task";         Jar = "services/task-service/build/libs"         },
    @{ Name = "schedule";     Jar = "services/schedule-service/build/libs"     },
    @{ Name = "notification"; Jar = "services/notification-service/build/libs" }
)

# ── 1. Build ────────────────────────────────────────────────────────────────
if (-not $SkipBuild) {
    Write-Host "`n==> [1/3] Buildando todos os servicos..." -ForegroundColor Cyan
    & ".\gradlew.bat" build -x test
    if ($LASTEXITCODE -ne 0) { Write-Error "Build falhou."; exit 1 }
} else {
    Write-Host "`n==> [1/3] Build pulado (-SkipBuild)." -ForegroundColor Yellow
}

# ── 2. Enviar JARs ──────────────────────────────────────────────────────────
Write-Host "`n==> [2/3] Enviando JARs para a VPS ($VPS)..." -ForegroundColor Cyan

foreach ($svc in $Services) {
    $jar = Get-ChildItem -Path $svc.Jar -Filter "*-SNAPSHOT.jar" | Select-Object -First 1
    if (-not $jar) {
        Write-Error "JAR nao encontrado em $($svc.Jar). Rode o build primeiro."
        exit 1
    }
    $remoteName = "$($svc.Name)-service.jar"
    Write-Host "  -> $($jar.Name) => $Dst/$remoteName"
    scp -i $Key $jar.FullName "${VPS}:${Dst}/${remoteName}"
    if ($LASTEXITCODE -ne 0) { Write-Error "SCP falhou para $remoteName"; exit 1 }
}

# ── 3. Enviar configs de infra (nginx + systemd) ────────────────────────────
Write-Host "`n==> [3/3] Enviando configs de infra..." -ForegroundColor Cyan

scp -i $Key "infra\nginx.conf"                    "${VPS}:/tmp/justdoit-nginx.conf"
scp -i $Key "infra\justdoit-auth.service"         "${VPS}:/tmp/justdoit-auth.service"
scp -i $Key "infra\justdoit-task.service"         "${VPS}:/tmp/justdoit-task.service"
scp -i $Key "infra\justdoit-schedule.service"     "${VPS}:/tmp/justdoit-schedule.service"
scp -i $Key "infra\justdoit-notification.service" "${VPS}:/tmp/justdoit-notification.service"

# ── Instruções finais ────────────────────────────────────────────────────────
Write-Host @"

=============================================================================
Arquivos enviados! Agora conecte na VPS e rode:

  ssh -i "$Key" $VPS

  # Instalar configs (primeira vez ou apos mudanca de nginx/systemd)
  sudo cp /tmp/justdoit-nginx.conf /etc/nginx/sites-available/justdoit
  sudo cp /tmp/justdoit-*.service /etc/systemd/system/
  sudo systemctl daemon-reload
  sudo nginx -t && sudo systemctl reload nginx

  # Reiniciar os servicos
  sudo systemctl restart justdoit-auth justdoit-task justdoit-schedule justdoit-notification

  # Verificar status
  sudo systemctl status justdoit-auth justdoit-task justdoit-schedule justdoit-notification

  # Ver logs em tempo real
  sudo journalctl -fu justdoit-auth
=============================================================================
"@ -ForegroundColor Green
