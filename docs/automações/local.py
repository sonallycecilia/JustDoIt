#!/usr/bin/env python3
"""
Automação para desenvolvimento local.
Uso:
  python docs/local.py start    — sobe banco + todos os serviços
  python docs/local.py stop     — para tudo
  python docs/local.py restart  — reinicia só os serviços (banco continua no ar)
  python docs/local.py db       — sobe só MySQL e Redis
"""
import subprocess
import sys
import time
import os

SERVICES = {
    "auth":         "auth-service",
    "task":         "task-service",
    "schedule":     "schedule-service",
    "notification": "notification-service",
}

def run(cmd, **kwargs):
    print(f"\n$ {' '.join(cmd)}")
    subprocess.run(cmd, check=True, **kwargs)

def start_db():
    print("\n[DB] Subindo MySQL e Redis...")
    run(["docker-compose", "-f", "infra/docker-compose.yml", "up", "-d"])
    print("Aguardando banco inicializar...")
    time.sleep(5)

def stop_db():
    print("\n[DB] Parando MySQL e Redis...")
    run(["docker-compose", "-f", "infra/docker-compose.yml", "down"])

def start_service(alias):
    name = SERVICES[alias]
    print(f"\n[{alias}] Iniciando {name}...")
    gradlew = "gradlew.bat" if sys.platform == "win32" else "./gradlew"
    subprocess.Popen(
        f'start "{name}" cmd /k {gradlew} :services:{name}:bootRun',
        shell=True
    )

def start_all():
    start_db()
    print("\nSubindo serviços (cada um abre em uma janela separada)...")
    for alias in SERVICES:
        start_service(alias)
        time.sleep(2)
    print("\nTodos os serviços iniciados.")
    print("\nEndereços locais:")
    print("  auth-service:         http://localhost:8080")
    print("  task-service:         http://localhost:8081")
    print("  schedule-service:     http://localhost:8082")
    print("  notification-service: http://localhost:8083")

def stop_services():
    """Fecha só as janelas dos serviços Spring (sem mexer no banco)."""
    if sys.platform == "win32":
        for alias in SERVICES:
            name = SERVICES[alias]
            subprocess.run(f'taskkill /FI "WINDOWTITLE eq {name}" /T /F', shell=True)
    else:
        print("[stop] Encerre as janelas dos serviços manualmente (não-Windows).")

def stop_all():
    print("\nParando serviços...")
    stop_services()
    stop_db()
    print("Tudo parado.")

def restart_services():
    """Reinicia só os serviços Java — útil após mudar código no backend. Mantém o
    banco no ar (não perde dados, libera as portas e evita 'port in use')."""
    print("\nReiniciando serviços (banco continua no ar)...")
    stop_services()
    time.sleep(2)  # dá tempo das portas liberarem antes de subir de novo
    for alias in SERVICES:
        start_service(alias)
        time.sleep(2)
    print("\nServiços reiniciados. (O banco não foi tocado.)")

def main():
    # Este script vive em docs/automações/, então a raiz do projeto fica dois níveis acima.
    os.chdir(os.path.join(os.path.dirname(__file__), "..", ".."))

    commands = ["start", "stop", "restart", "db"]
    if len(sys.argv) != 2 or sys.argv[1] not in commands:
        print(f"Uso: python docs/local.py [{' | '.join(commands)}]")
        sys.exit(1)

    cmd = sys.argv[1]
    if cmd == "start":
        start_all()
    elif cmd == "stop":
        stop_all()
    elif cmd == "restart":
        restart_services()
    elif cmd == "db":
        start_db()
        print("Banco no ar. Rode os serviços pelo IntelliJ.")

if __name__ == "__main__":
    main()
