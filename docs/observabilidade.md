# Observabilidade

O ambiente local provisiona Prometheus e Grafana para acompanhar principalmente
a exportação assíncrona do `task-service`.

## Iniciar

Crie `infra/.env` com as credenciais necessárias e execute:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql-justdoit redis-justdoit prometheus grafana
```

Com o `task-service` em `localhost:8081`, o Prometheus coleta
`/actuator/prometheus` a cada 15 segundos. Endereços locais:

| Serviço | Endereço |
|---|---|
| Prometheus | `http://localhost:9090` |
| Grafana | `http://localhost:3001` |

O Grafana provisiona automaticamente o painel **JustDoIt / Exportação
assíncrona**, com:

- duração P95 das exportações;
- memória da JVM;
- workers ativos, fila e saldo de jobs;
- registros e bytes gerados;
- erros, limites, rejeições e timeouts;
- P95 dos demais endpoints para identificar degradação durante exportações.

## Limites atuais

- Somente o `task-service` inclui o registry Prometheus e publica métricas.
- Os outros três serviços expõem `/actuator/health`, mas não
  `/actuator/prometheus`.
- Não há tracing distribuído nem correlação automática entre serviços.
- O Compose publica Prometheus e Grafana apenas em `127.0.0.1`.

Em produção, troque as credenciais padrão do Grafana e mantenha o endpoint de
métricas acessível somente pela rede de observabilidade.
