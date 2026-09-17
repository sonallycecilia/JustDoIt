# JustDoIt

Monorepo da aplicação de produtividade JustDoIt. A raiz contém somente os
arquivos compartilhados do projeto; frontend e backend possuem raízes de build
independentes e ficam em diretórios irmãos.

## Estrutura

```text
JustDoIt/
├── .github/workflows/       CI e deploy do monorepo
├── backend/                 API, infraestrutura e documentação operacional
│   ├── docs/                arquitetura, deploy, observabilidade e qualidade
│   ├── infra/               Docker Compose, Nginx, systemd e observabilidade
│   ├── libs/common/         código Java compartilhado
│   ├── quality-tests/       testes de carga com k6
│   ├── scripts/             qualidade, empacotamento e deploy
│   └── services/            microsserviços Spring Boot
├── frontend/                SPA React/Vite, testes e documentação do cliente
├── LICENSE
└── README.md
```

## Componentes

| Componente | Tecnologia | Porta local | Responsabilidade |
|---|---|---:|---|
| Frontend | React 18 e Vite 6 | 3000 | interface web e estado do cliente |
| `auth-service` | Spring Boot 3.4.1 | 8080 | cadastro, login, perfil e sessão |
| `task-service` | Spring Boot 3.4.1 | 8081 | tarefas, notas, tempo, ciclos e exportação |
| `schedule-service` | Spring Boot 3.4.1 | 8082 | agenda, planos semanais e análises |
| `notification-service` | Spring Boot 3.4.1 | 8083 | lembretes, notificações e suporte |

## Pré-requisitos

- JDK 21;
- Node.js 20 ou superior e npm;
- Docker com Compose;
- PowerShell ou shell compatível;
- Python 3 apenas para o orquestrador local opcional.

## Executar localmente

Crie `backend/infra/.env` com as variáveis do banco, Redis, JWT, integrações e
e-mail. O arquivo é ignorado pelo Git.

Backend:

```bash
cd backend
docker compose --env-file infra/.env -f infra/docker-compose.yml up -d mysql-justdoit redis-justdoit
./gradlew :services:auth-service:bootRun
./gradlew :services:task-service:bootRun
./gradlew :services:schedule-service:bootRun
./gradlew :services:notification-service:bootRun
```

No Windows, use `gradlew.bat` no lugar de `./gradlew`. Execute cada serviço em
um terminal próprio.

Frontend:

```bash
cd frontend
npm ci
npm run dev
```

Para iniciar a infraestrutura, os serviços e o frontend com um único comando:

```bash
python frontend/run.py start
```

O script também aceita `front`, `back`, `restart` e `stop`. A variável
`JUSTDOIT_BACKEND_DIR` permite informar outra localização para o backend.

## Testes e build

```bash
cd backend
./gradlew clean test --no-daemon --console=plain
./gradlew build
```

```bash
cd frontend
npm test
npm run build
npm run quality:all
```

## Documentação

- [Arquitetura do backend](backend/docs/arquitetura.md)
- [Deploy na VPS](backend/docs/deploy-vps.md)
- [Observabilidade](backend/docs/observabilidade.md)
- [Qualidade do backend](backend/docs/quality/)
- [Qualidade do frontend](frontend/docs/quality/)
- [Segurança da sessão no frontend](frontend/docs/security/session-storage-risk.md)

## CI/CD

- `Qualidade`: testes e métricas do backend em `backend/`;
- `Qualidade Frontend`: testes e métricas do frontend em `frontend/`;
- `Deploy Frontend`: build e publicação no GitHub Pages;
- `Deploy VPS`: empacotamento e implantação dos serviços do backend.

Antes de enviar mudanças, execute os testes do componente afetado e não
versione `.env`, credenciais, `node_modules`, `dist`, `build` ou relatórios
gerados.
