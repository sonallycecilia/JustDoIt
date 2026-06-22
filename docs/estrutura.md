# JustDoIt — Documentação da Estrutura Gerada

## Visão Geral

Arquitetura de microsserviços com 4 serviços Spring Boot 3.4.1 / Java 21, projeto multi-módulo Gradle, autenticação JWT stateless e PostgreSQL como banco de dados.

| Serviço | Porta | Banco |
|---|---|---|
| `auth-service` | 8080 | `justdoit_auth` |
| `task-service` | 8081 | `justdoit_task` |
| `schedule-service` | 8082 | `justdoit_schedule` |
| `notification-service` | 8083 | `justdoit_notification` |

---

## Estrutura de Arquivos

```
JustDoIt/
├── build.gradle.kts                  ← agregador raiz (plugins sem apply)
├── settings.gradle.kts               ← inclui os 4 submódulos
├── CLAUDE.md
│
├── auth-service/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── resources/application.yml
│       └── java/com/justdoit/auth/
│           ├── AuthServiceApplication.java
│           ├── config/
│           │   └── WebSecurityConfig.java
│           ├── security/
│           │   ├── JwtUtil.java
│           │   └── JwtAuthFilter.java
│           ├── model/
│           │   ├── User.java
│           │   └── JwtToken.java
│           ├── repository/
│           │   ├── UserRepository.java
│           │   └── JwtTokenRepository.java
│           ├── service/
│           │   └── AuthService.java
│           ├── controller/
│           │   └── AuthController.java
│           └── dto/
│               ├── RegisterRequest.java
│               ├── LoginRequest.java
│               └── AuthResponse.java
│
├── task-service/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── resources/application.yml
│       └── java/com/justdoit/task/
│           ├── TaskServiceApplication.java
│           ├── config/WebSecurityConfig.java
│           ├── security/
│           │   ├── JwtUtil.java
│           │   └── JwtAuthFilter.java
│           ├── model/
│           │   ├── enums/
│           │   │   ├── TaskStatus.java
│           │   │   ├── Priority.java
│           │   │   ├── CycleType.java
│           │   │   └── SessionType.java
│           │   ├── Category.java
│           │   ├── Task.java
│           │   ├── SubTask.java
│           │   ├── TaskModuleConfig.java
│           │   ├── TaskTimer.java
│           │   ├── TaskNote.java
│           │   ├── FocusSession.java
│           │   └── CycleConfig.java
│           ├── repository/
│           │   ├── CategoryRepository.java
│           │   ├── TaskRepository.java
│           │   ├── SubTaskRepository.java
│           │   ├── TaskModuleConfigRepository.java
│           │   ├── TaskTimerRepository.java
│           │   ├── TaskNoteRepository.java
│           │   ├── FocusSessionRepository.java
│           │   └── CycleConfigRepository.java
│           ├── service/
│           │   ├── TaskService.java
│           │   └── CategoryService.java
│           ├── controller/
│           │   ├── TaskController.java
│           │   └── CategoryController.java
│           └── dto/
│               ├── TaskRequest.java
│               ├── TaskResponse.java
│               ├── SubTaskRequest.java
│               ├── SubTaskResponse.java
│               ├── CategoryRequest.java
│               └── CategoryResponse.java
│
├── schedule-service/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── resources/application.yml
│       └── java/com/justdoit/schedule/
│           ├── ScheduleServiceApplication.java
│           ├── config/WebSecurityConfig.java
│           ├── security/
│           │   ├── JwtUtil.java
│           │   └── JwtAuthFilter.java
│           ├── model/
│           │   ├── enums/ScheduleStatus.java
│           │   ├── TimeBlock.java
│           │   ├── WeeklyPlan.java
│           │   └── WeeklySummary.java
│           ├── repository/
│           │   ├── TimeBlockRepository.java
│           │   ├── WeeklyPlanRepository.java
│           │   └── WeeklySummaryRepository.java
│           ├── service/
│           │   └── ScheduleService.java
│           ├── controller/
│           │   └── ScheduleController.java
│           └── dto/
│               ├── TimeBlockRequest.java
│               ├── TimeBlockResponse.java
│               ├── WeeklyPlanRequest.java
│               ├── WeeklyPlanResponse.java
│               └── WeeklySummaryResponse.java
│
└── notification-service/
    ├── build.gradle.kts
    └── src/main/
        ├── resources/application.yml
        └── java/com/justdoit/notification/
            ├── NotificationServiceApplication.java
            ├── config/WebSecurityConfig.java
            ├── security/
            │   ├── JwtUtil.java
            │   └── JwtAuthFilter.java
            ├── model/
            │   ├── enums/NotificationType.java
            │   ├── Notification.java
            │   └── NotificationPreference.java
            ├── repository/
            │   ├── NotificationRepository.java
            │   └── NotificationPreferenceRepository.java
            ├── service/
            │   └── NotificationService.java
            ├── controller/
            │   └── NotificationController.java
            └── dto/
                ├── CreateNotificationRequest.java
                ├── NotificationResponse.java
                ├── NotificationPreferenceRequest.java
                └── NotificationPreferenceResponse.java
```

---

## Gradle — Configuração Multi-módulo

### `settings.gradle.kts` (raiz)
```kotlin
rootProject.name = "justdoit"

include(
    "auth-service",
    "task-service",
    "schedule-service",
    "notification-service"
)
```

### `build.gradle.kts` (raiz — agregador)
```kotlin
plugins {
    id("org.springframework.boot") version "3.4.1" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    java
}
```

### `build.gradle.kts` (cada serviço — idêntico nos 4)
```kotlin
plugins {
    java
    id("org.springframework.boot")
    id("io.spring.dependency-management")
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    runtimeOnly("org.postgresql:postgresql")
    implementation("io.jsonwebtoken:jjwt-api:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.5")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.5")
    compileOnly("org.projectlombok:lombok:1.18.36")
    annotationProcessor("org.projectlombok:lombok:1.18.36")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    // + dependências de test
}
```

---

## Segurança JWT

### Fluxo
1. Cliente chama `POST /auth/register` ou `POST /auth/login`
2. `auth-service` gera um JWT (HS256, 24h) e salva na tabela `jwt_token`
3. Cliente envia o token no header `Authorization: Bearer <token>` em todas as outras requisições
4. Cada serviço tem um `JwtAuthFilter` que valida o token com a mesma `jwt.secret`
5. O `userId` (UUID) é extraído do token e usado nos controllers

### `JwtUtil` — API jjwt 0.12.5
```java
// Gerar (só auth-service)
Jwts.builder()
    .subject(userId.toString())
    .claim("email", email)
    .claim("profile", profile)
    .issuedAt(new Date())
    .expiration(new Date(System.currentTimeMillis() + 86_400_000L))
    .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)))
    .compact();

// Validar / extrair
Jwts.parser()
    .verifyWith(getKey())
    .build()
    .parseSignedClaims(token)
    .getPayload();
```

### `WebSecurityConfig` — diferenças entre serviços

| | auth-service | task / schedule / notification |
|---|---|---|
| Endpoint público | `/auth/**` | nenhum |
| `PasswordEncoder` bean | sim (`BCryptPasswordEncoder`) | não |
| Sessão | STATELESS | STATELESS |
| CORS | `http://localhost:3000` | `http://localhost:3000` |

### Extraindo `userId` nos controllers
```java
private UUID extractUserId(HttpServletRequest request) {
    String token = request.getHeader("Authorization").substring(7);
    return jwtUtil.extractUserId(token);
}
```

---

## Banco de Dados

### Pré-requisitos
PostgreSQL rodando em `localhost:5432`, usuário `postgres`, senha `postgres`.

```sql
CREATE DATABASE justdoit_auth;
CREATE DATABASE justdoit_task;
CREATE DATABASE justdoit_schedule;
CREATE DATABASE justdoit_notification;
```

O `ddl-auto=update` cria as tabelas automaticamente ao subir cada serviço.

### Tabelas por serviço

#### auth-service
| Tabela | Campos principais |
|---|---|
| `"user"` | `id UUID PK`, `name`, `email` (unique), `password_hash`, `birth_date`, `created_at`, `active` |
| `jwt_token` | `id UUID PK`, `token`, `user_id UUID`, `email`, `profile`, `expires_at` |

> A tabela `user` usa `@Table(name = "\"user\"")` por ser palavra reservada no PostgreSQL.

#### task-service
| Tabela | Campos principais |
|---|---|
| `category` | `id`, `user_id`, `name`, `color`, `description` |
| `task` | `id`, `user_id`, `category_id FK`, `title`, `description`, `status`, `priority`, `created_at`, `updated_at` |
| `subtask` | `id`, `parent_task_id FK→task`, `title`, `status`, `position` |
| `task_module_config` | `id`, `task_id FK` (1:1), `focus_enabled`, `cycle_enabled`, `priority_enabled`, `timer_enabled`, `notes_enabled` |
| `task_timer` | `id`, `task_id FK` (1:1), `estimated_minutes`, `actual_seconds`, `completed_at` |
| `task_note` | `id`, `task_id FK` (1:1), `content TEXT`, `created_at`, `updated_at` |
| `focus_session` | `id`, `task_id FK`, `focus_minutes`, `break_minutes`, `session_type`, `started_at`, `ended_at`, `completed` |
| `cycle_config` | `id`, `task_id FK` (1:1), `cycle_type`, `start_date`, `end_date`, `next_reset_date` |

#### schedule-service
| Tabela | Campos principais |
|---|---|
| `time_block` | `id`, `user_id`, `task_id`, `start_date_time`, `end_date_time`, `estimated_minutes`, `date` |
| `weekly_plan` | `id`, `user_id`, `week_start_date`, `week_end_date`, `status` |
| `weekly_summary` | `id`, `weekly_plan_id FK` (1:1), `total_estimated_minutes`, `total_actual_seconds`, `deviation_seconds`, `completed_tasks`, `total_tasks` |

#### notification-service
| Tabela | Campos principais |
|---|---|
| `notification` | `id`, `user_id`, `task_id`, `type`, `title`, `message`, `is_read`, `created_at` |
| `notification_preference` | `id`, `user_id` (unique), `notify_on_complete`, `notify_on_overdue`, `notify_on_cycle_reset` |

### ENUMs (salvos como STRING no banco)
| Enum | Valores |
|---|---|
| `TaskStatus` | `PENDING, IN_PROGRESS, COMPLETED, CANCELLED, OVERDUE` |
| `Priority` | `URGENT_IMPORTANT, NOT_URGENT_IMPORTANT, URGENT_NOT_IMPORTANT, NORMAL` |
| `CycleType` | `DAILY, WEEKLY, MONTHLY, ANNUAL` |
| `SessionType` | `FOCUS, BREAK` |
| `ScheduleStatus` | `OPEN, CLOSED` |
| `NotificationType` | `TASK_COMPLETED, TASK_OVERDUE, CYCLE_RESET, WEEKLY_SUMMARY` |

---

## Endpoints REST

### auth-service (porta 8080)
| Método | Endpoint | Body | Resposta |
|---|---|---|---|
| POST | `/auth/register` | `{ name, email, password, birthDate }` | `{ token }` |
| POST | `/auth/login` | `{ email, password }` | `{ token }` |

### task-service (porta 8081)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/tasks` | Criar tarefa |
| GET | `/tasks` | Listar tarefas do usuário |
| GET | `/tasks/{id}` | Buscar tarefa por ID |
| PUT | `/tasks/{id}` | Atualizar tarefa |
| DELETE | `/tasks/{id}` | Deletar tarefa |
| PATCH | `/tasks/{id}/complete` | Marcar como concluída |
| POST | `/tasks/{id}/subtasks` | Adicionar subtarefa |
| GET | `/tasks/{id}/subtasks/progress` | Progresso das subtarefas (0.0 a 1.0) |
| GET | `/categories` | Listar categorias |
| POST | `/categories` | Criar categoria |
| GET | `/categories/{id}` | Buscar categoria |
| PUT | `/categories/{id}` | Atualizar categoria |
| DELETE | `/categories/{id}` | Deletar categoria |

### schedule-service (porta 8082)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/time-blocks` | Criar bloco de tempo |
| GET | `/time-blocks?date=yyyy-MM-dd` | Blocos de um dia |
| POST | `/weekly-plans` | Criar plano semanal |
| PATCH | `/weekly-plans/{id}/close` | Fechar plano semanal |
| POST | `/weekly-plans/{id}/summary` | Gerar resumo semanal |
| GET | `/weekly-plans/{id}/summary` | Buscar resumo semanal |

### notification-service (porta 8083)
| Método | Endpoint | Descrição |
|---|---|---|
| POST | `/notifications` | Criar notificação |
| GET | `/notifications` | Todas as notificações do usuário |
| GET | `/notifications/unread` | Não lidas |
| PATCH | `/notifications/{id}/read` | Marcar como lida |
| GET | `/notifications/preferences` | Buscar preferências |
| PUT | `/notifications/preferences` | Atualizar preferências |

---

## Padrões adotados nas entidades

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "nome_da_tabela")
public class MinhaEntidade {

    @Id
    @GeneratedValue
    @UuidGenerator                      // org.hibernate.annotations.UuidGenerator
    private UUID id;

    @CreationTimestamp                  // org.hibernate.annotations.CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Enumerated(EnumType.STRING)        // enum salvo como texto no banco
    private MeuEnum status;

    @Builder.Default                    // obrigatório para valores default com @Builder
    private Boolean ativo = true;
}
```

## Padrões adotados nos DTOs

```java
// Records com validação Bean Validation
public record MeuRequest(
    @NotBlank String nome,
    @NotNull @Email String email,
    @NotNull UUID categoriaId
) {}

// Records de resposta (sem validação)
public record MeuResponse(UUID id, String nome, LocalDateTime criadoEm) {}
```

---

## Como rodar no IntelliJ

1. Abrir o projeto pela pasta raiz `JustDoIt/`
2. O IntelliJ detecta o `settings.gradle.kts` e carrega os 4 módulos
3. Se não carregar automaticamente: aba **Gradle** (direita) → botão `+` → selecionar `settings.gradle.kts` da raiz
4. Criar os bancos no PostgreSQL (ver seção acima)
5. Rodar cada serviço pelo `bootRun` na aba Gradle ou pela classe `*Application.java`

## Ordem recomendada para subir os serviços

1. `auth-service` (8080) — sempre primeiro, gera os tokens
2. `task-service` (8081)
3. `schedule-service` (8082)
4. `notification-service` (8083)
