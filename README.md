# JustDoIt — Gerenciador de Tarefas

O **JustDoIt** é uma plataforma web focada no gerenciamento de tarefas e produtividade pessoal por meio da metodologia de blocos de tempo (*time-blocking*). O sistema organiza demandas em contextos específicos e oferece monitoramento analítico de esforço.

---

## Estrutura do Projeto

Projeto multi-módulo **Spring Boot 3.4.1 / Java 21** gerenciado pelo Gradle, composto por 4 serviços independentes que compartilham um único banco MySQL, mais uma biblioteca comum.

```
JustDoIt/
├── libs/
│   └── common/                # Biblioteca compartilhada (validação JWT, filtro, exception handler)
├── services/
│   ├── auth-service/          # Autenticação, emissão de JWT e refresh tokens (8080)
│   ├── task-service/          # Tarefas, categorias, anotações e relatórios (8081)
│   ├── schedule-service/      # Blocos de tempo e plano semanal (8082)
│   └── notification-service/  # Notificações e preferências (8083)
└── infra/
    ├── docker-compose.yml
    └── nginx.conf             # Reverse proxy por prefixo de rota
```

Cada serviço segue o layout **feature-based**:

```
com.justdoit.<service>/
├── feature/<name>/   # Controller, Service, Entities, Repositories
├── integration/      # Clientes HTTP entre serviços (quando houver)
├── config/           # WebSecurityConfig (+ RateLimitFilter no auth)
└── shared/           # DTOs (records), Enums
```

> A validação de token (`JwtValidator`), o filtro de autenticação (`JwtAuthFilter`)
> e o `GlobalExceptionHandler` **não** ficam mais em cada serviço: vivem em
> `libs/common` e são reusados por todos. Nos controllers, o usuário autenticado
> chega via `@AuthenticationPrincipal UUID userId` (o filtro coloca o UUID como
> principal no SecurityContext).

---

## libs/common

Biblioteca Gradle compartilhada pelos quatro serviços. Contém o que antes era
copiado em cada um:

| Classe | Responsabilidade |
|---|---|
| `JwtValidator` | Valida access tokens (assinatura, `iss`/`aud`, `type=access`) e lê `sub`/`email`. Constantes `ISSUER`/`AUDIENCE` são a fonte única de verdade. |
| `JwtAuthFilter` | Autentica a request pelo header `Authorization` e põe o UUID do usuário como principal. |
| `GlobalExceptionHandler` + `ErrorResponse` | Tratamento padrão de validação (400). |
| `AuthTestSupport` (test-fixture) | `authenticatedUser(UUID)` para os testes de slice dos serviços. |

**A geração de token é exclusiva do auth-service** (`JwtUtil.generateAccessToken`).
O common só valida — nenhum outro serviço emite tokens.

---

## Serviços

### auth-service (8080)
Registro, login, emissão de access token (stateless, ~15 min) e rotação de refresh tokens.

| Classe | Responsabilidade |
|---|---|
| `AuthController` | `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/check-email`, `/auth/me`, `/auth/logout` |
| `AuthService` | Autenticação e emissão de token |
| `JwtUtil` | **Geração** do access token (validação fica no `JwtValidator` do common) |
| `User` | Entidade de usuário (tabela `users`) |
| `RefreshToken` (+ `RefreshTokenCleanupJob`) | Refresh tokens persistidos (tabela `refresh_token`), com limpeza periódica dos expirados |
| `RateLimitFilter` | Rate limit por IP nos endpoints públicos |

O access token é **stateless** — não é persistido. Contém `sub` (userId UUID),
`email`, `profile`, `type=access`, `iss=justdoit-auth-service`, `aud=justdoit-api`.

### task-service (8081)
Tarefas e todo o conteúdo de produtividade. `Task` é o aggregate root.

| Pacote | Conteúdo |
|---|---|
| `feature.task` | Aggregate root `Task` + `SubTask`, CRUD de tarefas/subtarefas, `OverdueTaskJob` |
| `feature.tasknote` | `TaskNote` — nota vinculada a UMA tarefa (`/tasks/{id}/note`) |
| `feature.note` | `Note` — anotações livres do usuário (aba **Anotações**, `/notes`) + bloco fixado no To Do (`/me/note`) |
| `feature.timer` | Cronômetro por tarefa (`/tasks/{id}/timer`) |
| `feature.focussession` | Sessões de foco (`/tasks/{id}/focus-sessions`) |
| `feature.cycle` | Tarefas cíclicas/recorrentes (`/tasks/{id}/cycle-config`) |
| `feature.moduleconfig` | Configuração de módulos por tarefa (`/tasks/{id}/module-config`) |
| `feature.report` | Relatório agregado por período (`/tasks/report`, consumido pelo schedule) |
| `feature.category` | Categorias (`/categories`) |
| `feature.userdata` | Purga dos dados do usuário na exclusão de conta (`DELETE /me/data`, chamada interna auth→task) |
| `integration` | `NotificationClient`, `TaskCompletedListener` (comunicação com o notification-service) |

**Anotações:** `Note` permite várias notas por usuário. No máximo uma é `pinned`
(a nota fixada, exibida no topo do To Do e servida por `/me/note` para manter
compatibilidade com o frontend). Os dados do antigo `UserNote`/`user_note` são
migrados automaticamente para `note` no boot (`UserNoteMigrationRunner`,
idempotente); a tabela `user_note` permanece como backup até remoção manual.

### schedule-service (8082)
Blocos de tempo e planos semanais. Feature única e coesa (`feature.schedule`) —
não foi fatiada em subfeatures por ser pequena.

| Classe | Responsabilidade |
|---|---|
| `WeeklyPlan` | Plano da semana (status `OPEN` / `CLOSED`) |
| `TimeBlock` | Bloco de tempo alocado em um dia |
| `WeeklySummary` | Resumo analítico da semana |

### notification-service (8083)

| Classe | Responsabilidade |
|---|---|
| `Notification` | Registro de notificação por usuário |
| `NotificationPreference` | Configurações de alerta do usuário |

---

## Autenticação (JWT Flow)

1. `/auth/register` ou `/auth/login` retornam um **access token** (jjwt 0.12.5, HS256)
   e um **refresh token**.
2. O access token é **stateless** (não é armazenado) e carrega `sub` (userId UUID),
   `email`, `profile`. O refresh token é persistido (tabela `refresh_token`).
3. Os demais endpoints exigem `Authorization: Bearer <access token>`.
4. Cada serviço valida o token com o `JwtValidator`/`JwtAuthFilter` do `libs/common`
   (mesmo segredo HMAC, sem dependência cruzada entre serviços). O auth-service é o
   único emissor.

---

## Roteamento (nginx)

O `infra/nginx.conf` roteia por prefixo de rota:

| Prefixo | Serviço |
|---|---|
| `/auth`, `/users` | auth-service (8080) |
| `/tasks`, `/categories`, `/notes`, `/me/note` | task-service (8081) |
| `/events`, `/time-blocks`, `/weekly-plans`, `/analytics` | schedule-service (8082) |
| `/notifications` | notification-service (8083) |

`/me/data` (purga de dados) e os endpoints `/internal/**` **não** são roteados
pelo nginx — são chamadas internas entre serviços.

---

## Infraestrutura

O banco `justdoit_db` é criado automaticamente (`createDatabaseIfNotExist=true`) e as
tabelas são gerenciadas pelo Hibernate (`ddl-auto=update`) — não há Flyway.

```bash
docker-compose -f infra/docker-compose.yml up -d
```

---

## Como Rodar

**Pré-requisitos:** Java 21, MySQL rodando.

```bash
# Subir um serviço específico
./gradlew :services:task-service:bootRun

# Build de tudo
./gradlew build

# Rodar testes (inclui libs:common)
./gradlew test
```

O frontend é servido separadamente (repositório `justdoit-frontend`). CORS está
configurado em todos os serviços para a origem do frontend.

---

## Tech Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Build | Gradle (multi-módulo) |
| Persistência | Spring Data JPA + MySQL (H2 nos testes) |
| Segurança | Spring Security 6.x — JWT stateless, CSRF desabilitado |
| JWT | jjwt 0.12.5 |
| Utilitários | Lombok, Bean Validation (jakarta.validation) |

---

## Contribuição

- **Branch:** `feature/JD-XX-nome-da-tarefa`
- **Commit:** descrição clara da mudança
- Todo código passa por Pull Request — nada vai direto para `main`.
