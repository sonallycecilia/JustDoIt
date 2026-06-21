#!/usr/bin/env bash
# =============================================================================
# JustDoIt — Script de setup da VPS (Ubuntu/Debian)
# Substitua as variáveis abaixo antes de executar.
# =============================================================================
set -euo pipefail

# ── CONFIGURAÇÕES — preencha antes de rodar ──────────────────────────────────
DUCKDNS_DOMAIN="justdoitapi.duckdns.org"
DUCKDNS_TOKEN="SEU_TOKEN_DUCKDNS"             # obtenha em duckdns.org após login
GITHUB_PAGES_ORIGIN="https://sonallycecilia.github.io"
DB_PASSWORD="root"                             # senha MySQL (mude em produção)
JWT_SECRET="justdoit-super-secret-key-2024-must-be-at-least-256-bits-long-hs256-ok"
APP_DIR="/opt/justdoit"
# ─────────────────────────────────────────────────────────────────────────────

echo "==> [1/7] Instalando pacotes base..."
sudo apt-get update -y
sudo apt-get install -y openjdk-21-jdk docker.io docker-compose nginx certbot python3-certbot-nginx curl

sudo systemctl enable --now docker nginx

echo "==> [2/7] Atualizando IP no DuckDNS..."
curl -s "https://www.duckdns.org/update?domains=${DUCKDNS_DOMAIN%%.*}&token=${DUCKDNS_TOKEN}&ip=" | grep -q OK && echo "DuckDNS OK" || echo "AVISO: DuckDNS update falhou"

echo "==> [3/7] Criando diretório da aplicação..."
sudo mkdir -p "$APP_DIR"
sudo chown "$USER":"$USER" "$APP_DIR"

echo "==> [4/7] Subindo MySQL e Redis via Docker Compose..."
cp "$(dirname "$0")/docker-compose.yml" "$APP_DIR/"
cd "$APP_DIR"
docker-compose up -d
echo "Aguardando MySQL inicializar (30s)..."
sleep 30

echo "==> [5/7] Configurando Nginx..."
NGINX_CONF="/etc/nginx/sites-available/justdoit"
sudo tee "$NGINX_CONF" > /dev/null <<NGINX
server {
    listen 80;
    server_name ${DUCKDNS_DOMAIN};
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    server_name ${DUCKDNS_DOMAIN};

    ssl_certificate     /etc/letsencrypt/live/${DUCKDNS_DOMAIN}/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/${DUCKDNS_DOMAIN}/privkey.pem;
    include             /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam         /etc/letsencrypt/ssl-dhparams.pem;

    location ~ ^/(auth|users)(\/|\$) {
        proxy_pass http://localhost:8080;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location ~ ^/(tasks|categories)(\/|\$) {
        proxy_pass http://localhost:8081;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location ~ ^/(events|time-blocks|weekly-plans|analytics)(\/|\$) {
        proxy_pass http://localhost:8082;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }

    location ~ ^/notifications(\/|\$) {
        proxy_pass http://localhost:8083;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
    }
}
NGINX

sudo ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/justdoit
sudo rm -f /etc/nginx/sites-enabled/default

# Certificado temporário para Let's Encrypt poder validar
sudo tee /etc/nginx/sites-available/justdoit-temp > /dev/null <<TMPNGINX
server {
    listen 80;
    server_name ${DUCKDNS_DOMAIN};
    root /var/www/html;
}
TMPNGINX
sudo ln -sf /etc/nginx/sites-available/justdoit-temp /etc/nginx/sites-enabled/justdoit
sudo nginx -t && sudo systemctl reload nginx

echo "==> [6/7] Obtendo certificado Let's Encrypt..."
sudo certbot --nginx -d "$DUCKDNS_DOMAIN" --non-interactive --agree-tos -m "sonallycecilia11@gmail.com" --redirect

# Ativa o config definitivo
sudo ln -sf "$NGINX_CONF" /etc/nginx/sites-enabled/justdoit
sudo rm -f /etc/nginx/sites-enabled/justdoit-temp /etc/nginx/sites-available/justdoit-temp
sudo nginx -t && sudo systemctl reload nginx

echo "==> [7/7] Criando serviços systemd..."

for SERVICE in auth task schedule notification; do
    PORT=$( [ "$SERVICE" = auth ] && echo 8080 \
         || ( [ "$SERVICE" = task ] && echo 8081 \
         || ( [ "$SERVICE" = schedule ] && echo 8082 \
         || echo 8083 ) ) )

    sudo tee "/etc/systemd/system/justdoit-${SERVICE}.service" > /dev/null <<UNIT
[Unit]
Description=JustDoIt ${SERVICE}-service
After=network.target docker.service

[Service]
User=$USER
WorkingDirectory=${APP_DIR}
ExecStart=/usr/bin/java -jar ${APP_DIR}/${SERVICE}-service.jar
Environment="CORS_ALLOWED_ORIGINS=${GITHUB_PAGES_ORIGIN}"
Environment="SPRING_DATASOURCE_PASSWORD=${DB_PASSWORD}"
Environment="JWT_SECRET=${JWT_SECRET}"
Restart=always
RestartSec=10

[Install]
WantedBy=multi-user.target
UNIT
done

sudo systemctl daemon-reload

cat <<INFO

=============================================================================
Setup concluído! Próximos passos manuais:

1. Copie os JARs compilados para ${APP_DIR}/:
   scp justdoit-backend/services/*/build/libs/*-SNAPSHOT.jar usuario@IP:${APP_DIR}/
   Renomeie cada JAR para: auth-service.jar, task-service.jar, schedule-service.jar, notification-service.jar

2. Inicie cada serviço:
   sudo systemctl enable --now justdoit-auth justdoit-task justdoit-schedule justdoit-notification

3. Atualize api.js no frontend:
   Substitua SEU_SUBDOMINIO por: ${DUCKDNS_DOMAIN%%.*}
   Faça commit e push para o GitHub Pages.

4. Verifique os logs:
   sudo journalctl -fu justdoit-auth
=============================================================================
INFO
