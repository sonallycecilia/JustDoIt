# 5. Ciclo de vida de uma tarefa

## 5.1 Estados

```mermaid
stateDiagram-v2
    [*] --> PENDING : POST /tasks

    PENDING --> COMPLETED : PATCH /tasks/{id}/complete<br/>completed_at = agora
    PENDING --> OVERDUE : OverdueTaskJob<br/>due_date anterior a hoje
    PENDING --> IN_PROGRESS : em uso pelo frontend

    IN_PROGRESS --> COMPLETED : PATCH /complete
    IN_PROGRESS --> OVERDUE : OverdueTaskJob

    OVERDUE --> COMPLETED : PATCH /complete<br/>ainda pode ser concluída depois do prazo

    COMPLETED --> PENDING : PATCH /tasks/{id}/reopen<br/>completed_at = null

    PENDING --> CANCELLED
    IN_PROGRESS --> CANCELLED

    COMPLETED --> [*] : DELETE /tasks/{id}<br/>cascade em subtarefas, timer,<br/>nota, sessões e ciclo
    CANCELLED --> [*]

    note right of OVERDUE
        O job só olha PENDING e IN_PROGRESS.
        Ao virar OVERDUE a tarefa sai do
        conjunto varrido — é isso que garante
        UMA notificação de atraso por tarefa,
        sem tabela de controle.
    end note
```

`CANCELLED` existe no enum `TaskStatus` mas nenhum endpoint transiciona para ele
hoje — está reservado.

## 5.2 O módulo de uma tarefa

Uma tarefa é o *aggregate root*: cada capacidade é uma entidade satélite, criada
sob demanda. Uma tarefa simples tem só a linha em `task`.

```mermaid
flowchart TD
    t["<b>Task</b><br/><small>título, descrição, prazo,<br/>prioridade, status</small>"]

    t --> sub["Subtarefas<br/><small>POST /tasks/{id}/subtasks<br/>GET .../progress → fração 0..1</small>"]
    t --> tim["Cronômetro<br/><small>/tasks/{id}/timer</small>"]
    t --> foc["Sessões de foco<br/><small>/tasks/{id}/focus-sessions</small>"]
    t --> cyc["Recorrência<br/><small>/tasks/{id}/cycle-config</small>"]
    t --> tn["Nota da tarefa<br/><small>/tasks/{id}/note</small>"]
    t --> mc["Módulos ligados/desligados<br/><small>/tasks/{id}/module-config</small>"]

    mc -. "controla o que o frontend exibe" .-> sub
    mc -. " " .-> tim
    mc -. " " .-> foc
    mc -. " " .-> cyc
    mc -. " " .-> tn

    classDef root fill:#e3f2fd,stroke:#1565c0,color:#0d47a1,stroke-width:2px
    classDef sat fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c
    class t root
    class sub,tim,foc,cyc,tn,mc sat
```

## 5.3 Conclusão de tarefa e a notificação

O ponto interessante: a notificação sai **depois do commit**, não durante a
transação.

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant TC as TaskController
    participant TS as TaskService
    participant DB as MySQL
    participant EV as ApplicationEventPublisher
    participant L as TaskCompletedListener
    participant NC as NotificationClient
    participant NS as notification-service

    F->>TC: PATCH /tasks/{id}/complete<br/>Authorization: Bearer ...
    TC->>TS: completeTask(id, userId, authHeader)
    Note over TC: o header segue junto de propósito:<br/>o notification-service será chamado com o<br/>token do próprio usuário, não com<br/>credencial de serviço

    rect rgb(232, 245, 233)
    Note over TS,DB: dentro de @Transactional
    TS->>DB: findByIdAndUserId
    TS->>DB: status = COMPLETED, completed_at = agora
    TS->>EV: publish TaskCompletedEvent<br/>{taskId, title, authHeader}
    end

    Note over EV: COMMIT

    EV->>L: @TransactionalEventListener(AFTER_COMMIT)
    L->>NC: notifyTaskCompleted
    NC->>NS: POST /notifications<br/>Authorization: Bearer do usuário<br/><small>connect 2 s / read 3 s</small>

    alt notification-service responde
        NS->>NS: INSERT notification<br/>userId sai do JWT, não do corpo
        NS-->>NC: 201
    else fora do ar, lento ou erro
        NS-->>NC: falha
        NC->>NC: log.warn e engole
        Note over NC: best-effort: a tarefa JÁ está concluída.<br/>Notificação não pode derrubar<br/>a operação de negócio
    end

    TC-->>F: 200 TaskResponse
```

### As duas decisões que esse diagrama mostra

**`AFTER_COMMIT`, não durante a transação.** Se a transação sofresse rollback
depois do envio, o usuário receberia notificação de uma conclusão que não
aconteceu. Publicando o evento dentro da transação mas consumindo depois do
commit, a notificação só existe se a conclusão existir.

**Best-effort com timeout curto.** O envio roda no caminho da request do usuário.
Um notification-service lento seguraria a resposta de "concluir tarefa" — daí
`connectTimeout` 2 s, `readTimeout` 3 s, e qualquer exceção virando `WARN`. O
usuário nunca vê erro por causa de uma notificação.
