# Observabilidade da exportação

Suba MySQL, Redis, Prometheus e Grafana:

```powershell
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql-justdoit redis-justdoit prometheus grafana
```

Com o `task-service` em `localhost:8081`, o Prometheus coleta
`/actuator/prometheus` a cada 15 segundos. O Grafana fica em
`http://localhost:3001` e provisiona automaticamente o dashboard
**JustDoIt / Exportação assíncrona**.

O dashboard acompanha:

- duração P95;
- memória JVM;
- workers ativos e saldo de jobs;
- registros e bytes gerados;
- erros, rejeições por capacidade, limites e timeouts;
- P95 dos demais endpoints para detectar degradação durante exportações.

Em produção, altere as credenciais padrão do Grafana e restrinja o endpoint
Prometheus à rede de observabilidade.
