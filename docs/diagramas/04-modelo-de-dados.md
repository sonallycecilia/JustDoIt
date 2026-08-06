# 4. Modelo de dados

Banco único `justdoit_db`, tabelas geradas pelo Hibernate com `ddl-auto=update`
(não há Flyway). Os quatro serviços compartilham o banco, mas **cada tabela
pertence a um serviço só** — a fronteira é de código, não de schema.

## 4.1 auth-service

```mermaid
erDiagram
    users {
        UUID id PK
        String name
        String email UK
        String password_hash "bcrypt"
        LocalDate birth_date
        String avatar_url "MEDIUMTEXT — data URI"
        LocalDateTime created_at
        Boolean active
    }

    refresh_token {
        UUID id PK
        String token_hash UK "SHA-256, 64 chars — nunca o valor cru"
        UUID user_id "não é FK: só o id"
        String email
        String profile
        Boolean remember_me "12 h ou 30 dias"
        LocalDateTime used_at "marca a rotação; permite detectar reuso"
        LocalDateTime expires_at
    }

    users ||..o| refresh_token : "um ativo por usuário"
```

## 4.2 task-service — `task` é o aggregate root

```mermaid
erDiagram
    task {
        UUID id PK
        UUID user_id
        UUID series_id "id da tarefa-modelo, nas ocorrências geradas"
        UUID category_id FK
        String title
        String description "TEXT"
        Integer estimated_minutes
        LocalDate due_date
        LocalTime due_time
        TaskStatus status "PENDING, IN_PROGRESS, COMPLETED, CANCELLED, OVERDUE"
        Priority priority "matriz de Eisenhower + NORMAL"
        LocalDateTime created_at
        LocalDateTime updated_at
        LocalDateTime completed_at "distinto de updated_at: alimenta relatórios"
    }

    category {
        UUID id PK
        UUID user_id
        String name
        String color
        String description
    }

    subtask {
        UUID id PK
        UUID parent_task_id FK
        String title
        TaskStatus status
        Integer position
    }

    task_timer {
        UUID id PK
        UUID task_id FK "unique"
        Integer estimated_minutes
        Long actual_seconds "acumulado, incrementado atomicamente"
        LocalDateTime completed_at
    }

    active_timer {
        UUID id PK
        UUID user_id UK "índice único: UM cronômetro por usuário"
        UUID task_id
        LocalDateTime started_at "servidor, nunca o cliente"
    }

    focus_session {
        UUID id PK
        UUID task_id FK
        Integer focus_minutes
        Integer break_minutes
        SessionType session_type "FOCUS, BREAK"
        LocalDateTime started_at
        LocalDateTime ended_at
        Boolean completed
    }

    cycle_config {
        UUID id PK
        UUID task_id FK "unique"
        CycleType cycle_type "DAILY, WEEKLY, BIWEEKLY, MONTHLY, ANNUAL, CUSTOM"
        LocalDate start_date
        LocalDate end_date
        LocalDate next_reset_date "informativo"
        IntervalUnit interval_unit "só CUSTOM: HOURS, DAYS"
        Integer interval_count "só CUSTOM"
        Integer total_occurrences "só CUSTOM, teto 365"
        LocalTime start_time "só CUSTOM"
    }

    task_note {
        UUID id PK
        UUID task_id FK "unique — nota DA tarefa"
        String content "TEXT"
    }

    task_module_config {
        UUID id PK
        UUID task_id FK "unique"
        Boolean focus_enabled
        Boolean cycle_enabled
        Boolean priority_enabled
        Boolean timer_enabled
        Boolean notes_enabled
    }

    note {
        UUID id PK
        UUID user_id "indexado"
        String title
        String content "TEXT"
        Boolean pinned "no máximo uma por usuário"
        LocalDateTime created_at
        LocalDateTime updated_at
    }

    category  ||--o{ task : "classifica; nulo = Genérico"
    task      ||--o{ subtask : "cascade ALL"
    task      ||--o| task_timer : "cascade ALL"
    task      ||--o| task_note : "cascade ALL"
    task      ||--o| task_module_config : "cascade ALL"
    task      ||--o| cycle_config : "cascade ALL"
    task      ||--o{ focus_session : "cascade ALL"
    task      ||--o{ task : "series_id — modelo gera ocorrências"
```

`active_timer` e `note` não têm FK para `task`: `active_timer` é por **usuário**
(o `task_id` é só referência) e `note` é anotação livre, sem tarefa.

## 4.3 schedule-service

```mermaid
erDiagram
    weekly_plan {
        UUID id PK
        UUID user_id
        LocalDate week_start_date
        LocalDate week_end_date
        ScheduleStatus status "OPEN, CLOSED"
    }

    time_block {
        UUID id PK
        UUID user_id
        UUID task_id "referência solta ao task-service"
        LocalDateTime start_date_time
        LocalDateTime end_date_time
        Integer estimated_minutes "o PLANEJADO"
        LocalDate date
    }

    weekly_summary {
        UUID id PK
        UUID weekly_plan_id FK "unique"
        Integer total_estimated_minutes "local: soma dos blocos"
        Long total_actual_seconds "vem do task-service"
        Long deviation_seconds "executado menos planejado"
        Integer completed_tasks "vem do task-service"
        Integer total_tasks
    }

    weekly_plan ||--o| weekly_summary : "cascade ALL"
```

## 4.4 notification-service

```mermaid
erDiagram
    notification {
        UUID id PK
        UUID user_id
        UUID task_id
        NotificationType type "TASK_COMPLETED, TASK_OVERDUE, CYCLE_RESET, WEEKLY_SUMMARY"
        String title
        String message "TEXT"
        Boolean is_read
        LocalDateTime created_at
    }

    notification_preference {
        UUID id PK
        UUID user_id UK
        Boolean notify_on_complete
        Boolean notify_on_overdue
        Boolean notify_on_cycle_reset
    }
```

## Decisões do modelo que valem explicar

**`user_id` como UUID solto, sem FK entre serviços.** `task.user_id`,
`time_block.user_id` e `notification.user_id` apontam para `users.id`, mas **não
há foreign key**. Se houvesse, o schema amarraria os serviços entre si e um
`DELETE` em `users` teria efeito colateral em tabelas de outro dono. A
consistência é mantida no código — ver [10-exclusao-de-conta.md](10-exclusao-de-conta.md).

**`completed_at` separado de `updated_at`.** `updated_at` muda em qualquer edição
(corrigir um título, mover de categoria). Para responder "quantas tarefas foram
concluídas nesta semana" é preciso um campo que só registre a conclusão — é o que
o schedule-service consome via `/tasks/report`.

**`active_timer` separado de `task_timer`.** `task_timer` guarda o **acumulado**
por tarefa; `active_timer` guarda a **contagem em curso** por usuário. Separar
permite o índice único em `user_id`, que é a garantia real de um cronômetro por
usuário — ver [06-cronometro.md](06-cronometro.md).

**`series_id` auto-referenciando `task`.** A tarefa que possui o `cycle_config` é
o **modelo** e fica com `series_id` nulo. As ocorrências geradas recebem
`series_id = id do modelo`. Isso permite contar, limitar e limpar as ocorrências
futuras de uma série sem tabela extra.
