# JustDoIt Backend

Backend do JustDoIt, uma aplicação de produtividade pessoal com tarefas, agenda,
cronômetro, ciclos semanais, notificações e exportação de dados.

> Estado verificado em 20/08/2026 contra `origin/main`, commit `dc6d9f2`. O
> código é a fonte de verdade quando houver divergência com um documento
> histórico.

## Visão rápida

O projeto é um monorepo Gradle com Java 21 e Spring Boot 3.4.1. Ele contém quatro
aplicações independentes e uma biblioteca compartilhada:

| Módulo | Porta | Responsabilidade principal |
|---|---:|---|
| `auth-service` | 8080 | cadastro, login, perfil, JWT, refresh token e Turnstile |
| `task-service` | 8081 | tarefas, notas, categorias, tempo, recorrência, fechamento semanal e exportação |
| `schedule-service` | 8082 | blocos de tempo, planos semanais e análises |
| `notification-service` | 8083 | lembretes, notificações e mensagens de suporte |
| `libs/common` | — | validação JWT, filtro de autenticação, erros e validações compartilhadas |

Os quatro serviços usam o mesmo banco MySQL, mas cada um mantém suas próprias
migrations e sua própria tabela de histórico do Flyway. O Nginx expõe uma API
única em `https://justdoitapi.duckdns.org` e encaminha cada prefixo ao serviço
correspondente.

## Estrutura

```text
JustDoIt/
├── docs/                    documentação do backend
├── infra/                   Compose, Nginx, systemd e observabilidade
├── libs/common/             código compartilhado
├── quality-tests/           testes de carga
├── scripts/                 qualidade, empacotamento e deploy
└── services/
    ├── auth-service/
    ├── task-service/
    ├── schedule-service/
    └── notification-service/
```

Dentro de cada serviço, o código é agrupado por funcionalidade em `feature/`,
com configurações transversais em `config/`, contratos comuns em `shared/` e
clientes entre serviços em `integration/`.

## Executar localmente

Pré-requisitos: JDK 21, Docker com Compose e PowerShell ou um shell compatível.

1. Crie um `infra/.env` local, ignorado pelo Git, e defina pelo menos
   `SPRING_DATASOURCE_PASSWORD`, `REDIS_PASSWORD` e `JWT_SECRET`.
2. Suba a infraestrutura local:

   ```bash
   docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql-justdoit redis-justdoit
   ```

3. Inicie os serviços, cada um em um terminal:

   ```bash
   ./gradlew :services:auth-service:bootRun
   ./gradlew :services:task-service:bootRun
   ./gradlew :services:schedule-service:bootRun
   ./gradlew :services:notification-service:bootRun
   ```

No Windows, use `gradlew.bat` no lugar de `./gradlew`.

Comandos úteis:

```bash
./gradlew test
./gradlew build
./gradlew :services:task-service:bootJar
```

## Configuração importante

As aplicações leem configuração do ambiente. As variáveis mais relevantes são:

| Variável | Uso |
|---|---|
| `SPRING_DATASOURCE_PASSWORD` | senha do MySQL |
| `JWT_SECRET` | assinatura e validação dos access tokens |
| `CORS_ALLOWED_ORIGINS` | origens permitidas, separadas por vírgula |
| `TASK_SERVICE_URL` | integração auth/schedule → task |
| `NOTIFICATION_SERVICE_URL` | integração task → notification |
| `INTERNAL_API_TOKEN` | autenticação do endpoint interno de notificações |
| `TURNSTILE_SECRET_KEY` | validação do Cloudflare Turnstile no login e cadastro |
| `EXPORT_STORAGE_PATH` | diretório privado dos arquivos exportados |
| `EXPORT_DOWNLOAD_SECRET` | assinatura dos links temporários de download |
| `PUBLIC_TASK_API_URL` | base pública usada nos links de exportação |

Produção não deve depender dos valores padrão de segredos presentes na
configuração de desenvolvimento.

## Documentação

- [Arquitetura atual](docs/arquitetura.md)
- [Deploy na VPS](docs/deploy-vps.md)
- [Observabilidade](docs/observabilidade.md)
- [Risco de memória na exportação](docs/quality/risco-exaustao-memoria-exportacao.md)
- [Correção funcional](docs/quality/correcao-funcional.md)
- [Desempenho](docs/quality/desempenho.md)
- [Segurança](docs/quality/seguranca.md)
- [Usabilidade](docs/quality/usabilidade.md)

Os quatro relatórios de atributos em `docs/quality/` são gerados pelo pipeline e
representam uma execução identificada por commit, data e ambiente. Eles não
devem ser lidos como garantia permanente de produção.

## Estado conhecido

- O pipeline de qualidade executa testes Gradle, métricas e validação das versões
  Flyway.
- O deploy da VPS ocorre após qualidade aprovada na `main` e possui health check
  e rollback dos JARs.
- O `task-service` expõe métricas Prometheus; os demais serviços expõem health
  checks.
- As migrations possuem versões únicas no estado verificado: auth até V1,
  notification até V5, schedule até V4 e task até V8.
- O Compose provisiona MySQL, Redis, Prometheus e Grafana. Os quatro serviços
  Spring são executados pelo Gradle localmente e por systemd na VPS.
