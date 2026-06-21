# JustDoIt — Arquitetura do Sistema

## Visão Geral

O JustDoIt é uma plataforma de gerenciamento de tarefas baseada em **time-blocking**. O backend segue uma arquitetura de **microsserviços desacoplados**, onde cada domínio de negócio é um serviço independente com seu próprio banco de dados. O frontend em React se comunica diretamente com cada serviço via REST.

---

## Diagrama de Comunicação

```
┌─────────────────────────────────────────────────────┐
│              Frontend — React (porta 3000)           │
└────┬──────────┬──────────┬──────────┬───────────────┘
     │          │          │          │
     ▼          ▼          ▼          ▼
┌─────────┐ ┌────────┐ ┌──────────┐ ┌──────────────┐
│  auth   │ │  task  │ │ schedule │ │notification  │
│ :8080   │ │ :8081  │ │  :8082   │ │    :8083     │
└────┬────┘ └───┬────┘ └────┬─────┘ └──────┬───────┘
     │          │           │              │
     ▼          ▼           ▼              ▼
┌─────────┐ ┌────────┐ ┌──────────┐ ┌──────────────┐
│justdoit │ │justdoit│ │ justdoit │ │   justdoit   │
│  _auth  │ │ _task  │ │_schedule │ │_notification │
│(Postgres│ │(Postgres│ │(Postgres)│ │  (Postgres)  │
└─────────┘ └────────┘ └──────────┘ └──────────────┘
```

> Os serviços **não se comunicam entre si** — são totalmente independentes. O `user_id` é propagado via token JWT, sem FK entre bancos.

---

## Serviços

### auth-service — porta 8080
Responsável por cadastro, login e emissão de tokens JWT.

**Endpoints públicos (sem token):**
| Método | Rota | Descrição |
|---|---|---|
| POST | `/auth/register` | Cadastrar novo usuário |
| POST | `/auth/login` | Autenticar e receber o token |

**Tabelas:**
- `"user"` — dados do usuário (id, name, email, password_hash, birth_date, created_at, active)
- `jwt_token` — tokens emitidos (token, user_id, email, profile, expires_at)

---

### task-service — porta 8081
Gerencia tarefas, subtarefas, categorias e todos os módulos de uma tarefa (timer, notas, foco, ciclo).

**Endpoints (requerem token):**
| Método | Rota | Descrição |
|---|---|---|
| GET | `/tasks` | Listar tarefas do usuário |
| POST | `/tasks` | Criar tarefa |
| GET | `/tasks/{id}` | Buscar tarefa |
| PUT | `/tasks/{id}` | Atualizar tarefa |
| DELETE | `/tasks/{id}` | Deletar tarefa |
| PATCH | `/tasks/{id}/complete` | Marcar como concluída |
| POST | `/tasks/{id}/subtasks` | Adicionar subtarefa |
| GET | `/tasks/{id}/subtasks/progress` | Progresso (0.0 a 1.0) |
| GET | `/categories` | Listar categorias |
| POST | `/categories` | Criar categoria |
| GET | `/categories/{id}` | Buscar categoria |
| PUT | `/categories/{id}` | Atualizar categoria |
| DELETE | `/categories/{id}` | Deletar categoria |

**Tabelas:**
- `category` — id, user_id, name, color, description
- `task` — id, user_id, category_id, title, description, status, priority, created_at, updated_at
- `subtask` — id, parent_task_id, title, status, position
- `task_module_config` — id, task_id (1:1), focus/cycle/priority/timer/notes enabled
- `task_timer` — id, task_id (1:1), estimated_minutes, actual_seconds, completed_at
- `task_note` — id, task_id (1:1), content, created_at, updated_at
- `focus_session` — id, task_id, focus_minutes, break_minutes, session_type, started_at, ended_at, completed
- `cycle_config` — id, task_id (1:1), cycle_type, start_date, end_date, next_reset_date

**ENUMs:**
- `TaskStatus`: PENDING · IN_PROGRESS · COMPLETED · CANCELLED · OVERDUE
- `Priority`: URGENT_IMPORTANT · NOT_URGENT_IMPORTANT · URGENT_NOT_IMPORTANT · NORMAL
- `CycleType`: DAILY · WEEKLY · MONTHLY · ANNUAL
- `SessionType`: FOCUS · BREAK

---

### schedule-service — porta 8082
Gerencia blocos de tempo (time-blocking) e planos semanais.

**Endpoints (requerem token):**
| Método | Rota | Descrição |
|---|---|---|
| POST | `/time-blocks` | Criar bloco de tempo |
| GET | `/time-blocks?date=yyyy-MM-dd` | Blocos de um dia |
| POST | `/weekly-plans` | Criar plano semanal |
| PATCH | `/weekly-plans/{id}/close` | Fechar plano (OPEN → CLOSED) |
| POST | `/weekly-plans/{id}/summary` | Gerar resumo semanal |
| GET | `/weekly-plans/{id}/summary` | Buscar resumo semanal |

**Tabelas:**
- `time_block` — id, user_id, task_id, start_date_time, end_date_time, estimated_minutes, date
- `weekly_plan` — id, user_id, week_start_date, week_end_date, status
- `weekly_summary` — id, weekly_plan_id (1:1), total_estimated_minutes, total_actual_seconds, deviation_seconds, completed_tasks, total_tasks

**ENUMs:**
- `ScheduleStatus`: OPEN · CLOSED

---

### notification-service — porta 8083
Gerencia notificações e preferências de notificação do usuário.

**Endpoints (requerem token):**
| Método | Rota | Descrição |
|---|---|---|
| POST | `/notifications` | Criar notificação |
| GET | `/notifications` | Todas as notificações |
| GET | `/notifications/unread` | Apenas não lidas |
| PATCH | `/notifications/{id}/read` | Marcar como lida |
| GET | `/notifications/preferences` | Buscar preferências |
| PUT | `/notifications/preferences` | Atualizar preferências |

**Tabelas:**
- `notification` — id, user_id, task_id, type, title, message, is_read, created_at
- `notification_preference` — id, user_id (unique), notify_on_complete, notify_on_overdue, notify_on_cycle_reset

**ENUMs:**
- `NotificationType`: TASK_COMPLETED · TASK_OVERDUE · CYCLE_RESET · WEEKLY_SUMMARY

---

## Autenticação JWT

### Fluxo
```
1. POST /auth/login  →  recebe { token: "eyJ..." }
2. Frontend salva o token (localStorage)
3. Toda requisição seguinte envia:
   Authorization: Bearer eyJ...
4. Cada serviço valida o token com a mesma jwt.secret
5. O userId (UUID) é extraído do token — sem consultar o banco de auth
```

### Estrutura do token
```
Header:  { alg: "HS256" }
Payload: { sub: "uuid-do-usuario", email: "...", profile: "USER", exp: ... }
```

### Validade
- Duração: **24 horas**
- Algoritmo: **HS256**
- Biblioteca: **jjwt 0.12.5**

### Em caso de token expirado
O backend retorna **HTTP 403**. O frontend redireciona para `/login`.

---

## Segurança

| Configuração | Valor |
|---|---|
| Sessão | STATELESS (sem cookies, sem sessão server-side) |
| CSRF | Desabilitado |
| CORS | Liberado para `http://localhost:3000` |
| Senha | BCrypt (hash no banco, nunca texto puro) |
| Endpoints públicos | Apenas `/auth/register` e `/auth/login` |

---

## Stack Tecnológica

### Backend
| Item | Versão |
|---|---|
| Java | 21 |
| Spring Boot | 3.4.1 |
| Spring Security | 6.x |
| Spring Data JPA | 3.4.x |
| Hibernate | 6.x |
| PostgreSQL Driver | gerenciado pelo Spring Boot |
| jjwt | 0.12.5 |
| Lombok | 1.18.36 |
| Build | Gradle (multi-módulo) |

### Frontend
| Item | Valor |
|---|---|
| Framework | React |
| Porta | 3000 |
| Comunicação | REST (fetch API) |
| Token | localStorage |

### Banco de Dados
| Serviço | Banco |
|---|---|
| auth-service | `justdoit_auth` |
| task-service | `justdoit_task` |
| schedule-service | `justdoit_schedule` |
| notification-service | `justdoit_notification` |

> Cada serviço tem seu próprio banco — **database-per-service pattern**. Não há joins entre bancos.

---

## Gradle Multi-módulo

```
JustDoIt/
├── settings.gradle.kts   ← inclui os 4 submódulos
├── build.gradle.kts      ← agregador raiz
├── auth-service/
│   └── build.gradle.kts
├── task-service/
│   └── build.gradle.kts
├── schedule-service/
│   └── build.gradle.kts
└── notification-service/
    └── build.gradle.kts
```

Para rodar um serviço:
```bash
# Pela raiz
./gradlew :auth-service:bootRun

# Ou dentro da pasta do serviço
./gradlew bootRun
```

---

## Ordem de Inicialização

```
1. PostgreSQL (banco de dados)
2. auth-service    :8080  ← sempre primeiro
3. task-service    :8081
4. schedule-service:8082
5. notification-service:8083
6. Frontend React  :3000
```

O `ddl-auto=update` cria as tabelas automaticamente na primeira inicialização de cada serviço.
