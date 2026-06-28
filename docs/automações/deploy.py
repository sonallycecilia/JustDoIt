#!/usr/bin/env python3
import subprocess
import sys

VPS_USER    = "ubuntu"
VPS_HOST    = "137.131.139.107"
VPS_DIR     = "/opt/justdoit"
VPS_SSH_KEY = r"C:\Users\sonal\Documents\ssh-keys\ssh-key-2026-06-22.pem"

SERVICES = {
    "auth":         "auth-service",
    "task":         "task-service",
    "schedule":     "schedule-service",
    "notification": "notification-service",
}

def run(cmd, **kwargs):
    print(f"\n$ {' '.join(cmd)}")
    result = subprocess.run(cmd, **kwargs)
    if result.returncode != 0:
        print(f"Erro: comando falhou com código {result.returncode}")
        sys.exit(result.returncode)

def ssh(command):
    run(["ssh", "-i", VPS_SSH_KEY, f"{VPS_USER}@{VPS_HOST}", command])

def scp(local, remote):
    run(["scp", "-i", VPS_SSH_KEY, local, f"{VPS_USER}@{VPS_HOST}:{remote}"])

def setup():
    print("\n{'='*50}")
    print("  Setup inicial da VPS")
    print(f"{'='*50}")

    print("\n[1/6] Instalando Java 21, Docker e Nginx...")
    ssh("sudo apt update && sudo apt install -y openjdk-21-jdk docker.io docker-compose nginx certbot python3-certbot-nginx")

    print("\n[2/6] Habilitando Docker...")
    ssh("sudo systemctl enable docker && sudo usermod -aG docker ubuntu")

    print("\n[3/6] Copiando arquivos de infraestrutura...")
    scp("infra/docker-compose.yml", VPS_DIR)
    scp("infra/.env.example",       f"{VPS_DIR}/.env")
    scp("infra/nginx.conf",         VPS_DIR)
    for alias in SERVICES:
        scp(f"infra/justdoit-{alias}.service", VPS_DIR)

    print("\n[4/6] Instalando serviços systemd...")
    ssh(f"sudo cp {VPS_DIR}/justdoit-*.service /etc/systemd/system/ && sudo systemctl daemon-reload")
    services = " ".join(f"justdoit-{a}" for a in SERVICES)
    ssh(f"sudo systemctl enable {services}")

    print("\n[5/6] Subindo MySQL e Redis...")
    ssh(f"cd {VPS_DIR} && sudo docker-compose --env-file .env up -d")

    print("\n[6/6] Configurando Nginx (HTTP temporário para certbot)...")
    nginx_tmp = (
        "server { "
        "listen 80; "
        "server_name justdoitapi.duckdns.org; "
        "location / { return 200 'ok'; } "
        "}"
    )
    ssh(f"echo '{nginx_tmp}' | sudo tee /etc/nginx/sites-available/justdoit > /dev/null")
    ssh("sudo ln -sf /etc/nginx/sites-available/justdoit /etc/nginx/sites-enabled/justdoit")
    ssh("sudo rm -f /etc/nginx/sites-enabled/default")
    ssh("sudo nginx -t && sudo systemctl reload nginx")

    print("\nSetup concluído.")
    print("\nPróximos passos na VPS:")
    print("  1. Garanta que justdoitapi.duckdns.org aponta para este IP no DuckDNS")
    print("  2. Emita o certificado SSL:")
    print("       sudo certbot --nginx -d justdoitapi.duckdns.org")
    print("  3. Aplique o nginx.conf definitivo:")
    print(f"       sudo cp {VPS_DIR}/nginx.conf /etc/nginx/sites-available/justdoit")
    print("       sudo nginx -t && sudo systemctl reload nginx")
    print("  4. Inicie os serviços:")
    print("       sudo systemctl start justdoit-auth justdoit-task justdoit-schedule justdoit-notification")

def deploy(alias):
    name = SERVICES[alias]
    jar_local = f"services/{name}/build/libs/{name}-0.0.1-SNAPSHOT.jar"
    jar_remote = f"{VPS_DIR}/{name}.jar"
    systemd_name = f"justdoit-{alias}"

    print(f"\n{'='*50}")
    print(f"  Deploy: {name}")
    print(f"{'='*50}")

    print("\n[1/3] Gerando JAR...")
    gradlew = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    run([gradlew, f":services:{name}:bootJar"])

    print("\n[2/3] Copiando para a VPS...")
    scp(jar_local, jar_remote)

    print("\n[3/3] Reiniciando serviço...")
    ssh(f"sudo systemctl restart {systemd_name}")

    print(f"\nDeploy de {name} concluído.")

def main():
    valid = ["setup"] + list(SERVICES.keys()) + ["all"]

    if len(sys.argv) != 2 or sys.argv[1] not in valid:
        print(f"Uso: python deploy.py [{' | '.join(valid)}]")
        sys.exit(1)

    arg = sys.argv[1]

    if arg == "setup":
        setup()
    elif arg == "all":
        for alias in SERVICES:
            deploy(alias)
        print("\nTudo pronto.")
    else:
        deploy(arg)
        print("\nTudo pronto.")

if __name__ == "__main__":
    main()
