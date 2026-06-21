# JustDoIt — Gerenciador de Tarefas

O **JustDoIt** é uma plataforma web focada no gerenciamento de tarefas e produtividade pessoal por meio da metodologia de blocos de tempo (*time-blocking*). O sistema organiza demandas em contextos específicos e oferece monitoramento analítico de esforço.

---

## Estrutura do Projeto

Projeto multi-módulo **Spring Boot 3.4.1 / Java 21** gerenciado pelo Gradle, composto por 4 serviços independentes que compartilham um único banco MySQL.

```
JustDoIt/
├── services/
│   ├── auth-service/          # Autenticação e JWT
│   ├── task-service/          # Tarefas e categorias
│   ├── schedule-service/      # Blocos de tempo e plano semanal
│   └── notification-service/  # Notificações e preferências
├── frontend/                  # Interface web
└── infra/
    └── docker-compose.yml     # MySQL + Redis
```

Cada serviço segue o layout **feature-based**:

```
com.justdoit.<service>/
├── feature/<name>/   # Controller, Service, Entities, Repositories
├── config/           # JwtAuthFilter, JwtUtil, WebSecurityConfig
└── shared/           # DTOs (records), Enums, GlobalExceptionHandler
```

---

## Serviços

### auth-service
Responsável por registro, login e emissão de tokens JWT.

| Classe | Responsabilidade |
|---|---|
| `AuthController` | `POST /auth/register`, `POST /auth/login` |
| `AuthService` | Lógica de autenticação e geração de token |
| `User` | Entidade de usuário |
| `JwtToken` | Tokens emitidos (tabela `jwt_token`) |

### task-service
Gerencia tarefas e categorias. `Task` é o aggregate root.

| Pacote | Classes principais |
|---|---|
| `feature.task` | `Task`, `SubTask`, `TaskModuleConfig`, `TaskTimer`, `TaskNote`, `FocusSession`, `CycleConfig` |
| `feature.category` | `Category` |

### schedule-service
Gerencia blocos de tempo e planos semanais.

| Classe | Responsabilidade |
|---|---|
| `WeeklyPlan` | Plano da semana (status: `OPEN` / `CLOSED`) |
| `TimeBlock` | Bloco de tempo alocado em um dia |
| `WeeklySummary` | Resumo analítico da semana |

### notification-service
Gerencia notificações e preferências do usuário.

| Classe | Responsabilidade |
|---|---|
| `Notification` | Registro de notificação por usuário |
| `NotificationPreference` | Configurações de alerta do usuário |

---

## Autenticação (JWT Flow)

1. `/auth/register` ou `/auth/login` retornam um token JWT (jjwt 0.12.5, HS256).
2. O token é armazenado na tabela `jwt_token` e contém `sub` (userId UUID), `email` e `profile`.
3. Todos os outros endpoints exigem `Authorization: Bearer <token>`.
4. Cada serviço valida o token via `JwtAuthFilter` (sem dependência cruzada entre serviços).

---

## Infraestrutura

| Componente | Tecnologia |
|---|---|
| Banco de dados | MySQL |
| Cache | Redis |

Subir a infra:

```bash
docker-compose -f infra/docker-compose.yml up -d
```

O banco `justdoit_db` é criado automaticamente (`createDatabaseIfNotExist=true`) e as tabelas são gerenciadas pelo Hibernate (`ddl-auto=update`).

---

## Como Rodar

**Pré-requisitos:** Java 21, MySQL e Redis rodando.

```bash
# Subir todos os serviços
./gradlew bootRun

# Build sem testes
./gradlew build -x test

# Rodar testes
./gradlew test
```

O frontend é servido separadamente. CORS está configurado em todos os serviços para a origem do frontend.

---

## Tech Stack

| Camada | Tecnologia |
|---|---|
| Linguagem | Java 21 |
| Framework | Spring Boot 3.4.1 |
| Build | Gradle (multi-módulo) |
| Persistência | Spring Data JPA + MySQL |
| Cache | Redis (`spring-boot-starter-data-redis`) |
| Segurança | Spring Security 6.x — JWT stateless, CSRF desabilitado |
| JWT | jjwt 0.12.5 |
| Utilitários | Lombok, Bean Validation (jakarta.validation) |

---

## Contribuição

- **Branch:** `feature/JD-XX-nome-da-tarefa`
- **Commit:** `[JD-XX] Descrição clara da mudança`
- Todo código passa por Pull Request — nada vai direto para `main`.
