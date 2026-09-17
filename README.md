# JustDoIt

O JustDoIt é uma aplicação de produtividade pessoal para tarefas, agenda,
cronômetro, notas, ciclos semanais, notificações, análises e exportação de dados.
Este repositório reúne o frontend React e quatro microsserviços Spring Boot sem
retirar a independência de build e execução de cada parte.

## Arquitetura

| Componente | Tecnologia | Porta local | Responsabilidade |
|---|---|---:|---|
| Frontend | React 18 e Vite 6 | 3000 | interface web e estado do cliente |
| `auth-service` | Spring Boot 3.4.1 | 8080 | cadastro, login, perfil, JWT, refresh token e Turnstile |
| `task-service` | Spring Boot 3.4.1 | 8081 | tarefas, notas, categorias, tempo, recorrência, ciclos e exportação |
| `schedule-service` | Spring Boot 3.4.1 | 8082 | blocos de tempo, planos semanais e análises |
| `notification-service` | Spring Boot 3.4.1 | 8083 | lembretes, notificações e mensagens de suporte |
| `libs/common` | Java 21 | — | autenticação, erros e validações compartilhadas |

Os serviços usam o mesmo MySQL, mas mantêm migrations e histórico Flyway
independentes. Em produção, o Nginx publica a API em
`https://justdoitapi.duckdns.org`; o frontend é publicado no GitHub Pages com o
domínio `https://justdoit-app.duckdns.org`.

## Estrutura

```text
JustDoIt/
├── .github/workflows/       qualidade e deploy do frontend e backend
├── frontend/                SPA React/Vite, testes e documentação própria
├── docs/                    arquitetura, operação e qualidade do backend
├── infra/                   Compose, Nginx, systemd e observabilidade
├── libs/common/             código Java compartilhado
├── quality-tests/           testes de carga
├── scripts/                 qualidade, empacotamento e deploy do backend
└── services/
    ├── auth-service/
    ├── task-service/
    ├── schedule-service/
    └── notification-service/
```

O backend permanece na raiz para preservar seus caminhos Gradle e operacionais.
O frontend mantém seu próprio `package.json`, lockfile, configuração Vite e
testes dentro de `frontend/`.

## Pré-requisitos

- JDK 21;
- Node.js 20 ou superior e npm;
- Docker com Compose;
- PowerShell ou shell compatível;
- Python 3 apenas para usar o orquestrador opcional `frontend/run.py`.

## Configuração

Crie `infra/.env` localmente. Esse arquivo é ignorado pelo Git e não deve ser
commitado. As aplicações e workflows utilizam, conforme o ambiente, variáveis
com estes nomes:

| Grupo | Variáveis principais |
|---|---|
| Banco e cache | `MYSQL_DATABASE`, `MYSQL_ROOT_PASSWORD`, `SPRING_DATASOURCE_PASSWORD`, `REDIS_PASSWORD` |
| Autenticação | `JWT_SECRET`, `JWT_ACCESS_EXPIRATION_MS`, `JWT_REFRESH_EXPIRATION_MS`, `TURNSTILE_SECRET_KEY` |
| Integração | `CORS_ALLOWED_ORIGINS`, `TASK_SERVICE_URL`, `NOTIFICATION_SERVICE_URL`, `INTERNAL_API_TOKEN`, `PUBLIC_TASK_API_URL` |
| Exportação | `EXPORT_STORAGE_PATH`, `EXPORT_DOWNLOAD_SECRET`, `EXPORT_MAX_RECORDS`, `EXPORT_MAX_FILE_SIZE_BYTES` |
| E-mail | `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `BUG_REPORT_FROM`, `BUG_REPORT_RECIPIENT` |
| Observabilidade | `GRAFANA_ADMIN_USER`, `GRAFANA_ADMIN_PASSWORD` |
| Frontend | `VITE_TURNSTILE_SITE_KEY` |

Não use valores de desenvolvimento como credenciais de produção. Valores reais
devem ficar em arquivos ignorados, secrets do GitHub ou no ambiente de execução.

## Executar localmente

### Infraestrutura e backend

```bash
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql-justdoit redis-justdoit
./gradlew :services:auth-service:bootRun
./gradlew :services:task-service:bootRun
./gradlew :services:schedule-service:bootRun
./gradlew :services:notification-service:bootRun
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`. Execute cada serviço em
um terminal próprio.

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

O Vite escuta `127.0.0.1:3000` com porta fixa, compatível com o CORS local do
backend.

### Orquestração opcional

Com Python 3 disponível, o script abaixo pode subir a infraestrutura, os quatro
serviços e o frontend:

```bash
python frontend/run.py start
```

Também estão disponíveis os comandos `front`, `back`, `restart` e `stop`. A
variável `JUSTDOIT_BACKEND_DIR` permite substituir a raiz do backend quando
necessário.

## Testes e build

Backend:

```bash
./gradlew clean test --no-daemon --console=plain
./gradlew build
./gradlew :services:task-service:bootJar
```

Frontend:

```bash
cd frontend
npm test
npm run build
npm run quality:all
```

Os gates completos do frontend incluem Vitest, Lighthouse, axe-core/Playwright,
responsividade e proteção do ciclo de sessão. Os relatórios Gradle/JaCoCo e os
artefatos de qualidade são gerados localmente em diretórios ignorados pelo Git.

Na validação local da migração em 17/09/2026, passaram 415 testes do backend,
129 testes do frontend, o build Vite e `docker compose config --quiet`.

## CI/CD

| Workflow | Gatilho | Resultado |
|---|---|---|
| `Qualidade` | push na `main`, pull request ou manual | testes e métricas do backend |
| `Qualidade Frontend` | push na `main`, pull request ou manual | testes e métricas do frontend |
| `Deploy Frontend (GitHub Pages)` | mudança em `frontend/` na `main` ou manual | build e publicação do frontend |
| `Deploy VPS` | sucesso de `Qualidade` na `main` ou manual | pacote, implantação e health check do backend |
| `Checar TURNSTILE_SECRET_KEY na VPS` | manual | diagnóstico sem revelar o valor |

Um push ou merge na `main` pode produzir efeitos de deploy. Antes de publicar
uma branch de migração, revise environments, secrets, Pages, proteção de branch
e os gatilhos dos workflows no repositório de destino.

## Histórico do frontend

O frontend foi importado com `git subtree` sem squash. O commit de origem
`aa7f77d913857c6c2d6887b62b8df87a6e741228` e seus ancestrais permanecem
alcançáveis, com autoria e hashes originais.

```bash
git log --graph --oneline
git log aa7f77d
git log -- frontend
```

## Documentação

- [Frontend](frontend/README.md)
- [Arquitetura do backend](docs/arquitetura.md)
- [Deploy na VPS](docs/deploy-vps.md)
- [Observabilidade](docs/observabilidade.md)
- [Qualidade do backend](docs/quality/)
- [Qualidade do frontend](frontend/docs/quality/)
- [Risco de sessão no frontend](frontend/docs/security/session-storage-risk.md)

Os relatórios de qualidade representam execuções identificadas por commit, data
e ambiente; não constituem garantia permanente do comportamento em produção.

## Contribuição

1. Crie uma branch a partir da base acordada.
2. Mantenha mudanças de frontend em `frontend/` e mudanças de backend nos
   módulos correspondentes.
3. Não versione `.env`, credenciais, `node_modules`, `dist`, `build` ou
   relatórios gerados.
4. Execute os testes e builds afetados antes de abrir o pull request.
5. Descreva no pull request qualquer impacto em migrations, variáveis,
   workflows ou deploy.

Não faça merge na `main` sem revisar os workflows que podem publicar no GitHub
Pages ou implantar a VPS.
