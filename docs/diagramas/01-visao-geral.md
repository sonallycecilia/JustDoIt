# 1. Visão geral — as peças e quem fala com quem

## Containers

```mermaid
flowchart TB
    user(["Usuário"])

    subgraph client["Cliente"]
        front["Frontend<br/><small>SPA em React + Vite<br/>repositório separado</small>"]
    end

    subgraph edge["Borda"]
        nginx["nginx<br/><small>reverse proxy por prefixo de rota<br/>TLS, HSTS, security headers</small>"]
    end

    subgraph backend["Backend — Gradle multi-módulo, Spring Boot 3.4.1 / Java 21"]
        auth["auth-service<br/><small>:8080</small>"]
        task["task-service<br/><small>:8081</small>"]
        sched["schedule-service<br/><small>:8082</small>"]
        notif["notification-service<br/><small>:8083</small>"]
        common[["libs/common<br/><small>JwtValidator, JwtAuthFilter,<br/>GlobalExceptionHandler, TextoSeguro</small>"]]
    end

    db[("MySQL<br/><small>justdoit_db — banco único<br/>compartilhado pelos 4 serviços</small>")]

    user --> front
    front -- "HTTPS + Bearer token" --> nginx

    nginx -- "/auth/**, /users/**" --> auth
    nginx -- "/tasks/**, /categories/**, /notes/**,<br/>/me/note, /me/export, /analytics/categories" --> task
    nginx -- "/events/**, /time-blocks/**,<br/>/weekly-plans/**, /analytics/**" --> sched
    nginx -- "/notifications/**" --> notif

    auth -. "usa" .-> common
    task -. "usa" .-> common
    sched -. "usa" .-> common
    notif -. "usa" .-> common

    auth --> db
    task --> db
    sched --> db
    notif --> db

    classDef cli fill:#ede7f6,stroke:#4527a0,color:#311b92
    classDef srv fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef inf fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef lib fill:#fff8e1,stroke:#ff8f00,color:#e65100
    class front cli
    class auth,task,sched,notif srv
    class db,nginx inf
    class common lib
```

## Comunicação entre serviços

Não há service discovery, message broker nem API gateway com lógica. Os serviços
se falam por **HTTP direto**, e sempre há um dos dois padrões abaixo.

```mermaid
flowchart LR
    subgraph presente["Padrão A — usuário presente: repassa o token dele"]
        direction TB
        t1["task-service"] -- "POST /notifications<br/>Authorization: Bearer do usuário" --> n1["notification-service"]
        s1["schedule-service"] -- "GET /tasks/report<br/>Authorization: Bearer do usuário" --> t2["task-service"]
        a1["auth-service"] -- "DELETE /me/data<br/>Authorization: Bearer do usuário" --> t3["task-service"]
    end

    subgraph ausente["Padrão B — job, sem usuário: segredo compartilhado"]
        direction TB
        j1["OverdueTaskJob<br/><small>task-service</small>"] -- "POST /internal/notifications<br/>X-Internal-Token" --> n2["notification-service<br/><small>endpoint AINDA NÃO EXISTE</small>"]
    end

    classDef srv fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef job fill:#fff3e0,stroke:#ef6c00,color:#e65100
    classDef gap fill:#ffebee,stroke:#c62828,color:#b71c1c
    class t1,t2,t3,s1,a1,n1 srv
    class j1 job
    class n2 gap
```

## O que explicar em cima disso

**Por que 4 serviços com um banco só.** A separação é de *responsabilidade e
deploy* (cada serviço sobe, cai e escala sozinho), não de dados. Um banco único
elimina transação distribuída e mantém o projeto operável — o custo é que o
isolamento de dados é por convenção, não pelo schema.

**Como o token atravessa os serviços sem acoplá-los.** O auth-service é o único
emissor de token. Os outros três só *validam*, com o mesmo segredo HMAC, usando
o `JwtValidator` de `libs/common`. Nenhum serviço precisa chamar o auth para
autenticar uma request — não existe dependência de runtime entre eles no caminho
de autenticação.

**Por que o padrão A é o normal.** Repassar o token do usuário significa que a
chamada entre serviços passa pela mesma autorização de uma chamada do navegador:
o serviço chamado extrai o `userId` do JWT e nunca aceita um `userId` vindo no
corpo. Não existe credencial de serviço com poder amplo.

**A pendência do padrão B.** `NotificationClient.notifyTaskOverdue` faz `POST
/internal/notifications`, mas esse endpoint **não está implementado** no
notification-service. Como o cliente engole qualquer falha (é *best-effort*), o
efeito hoje é: a tarefa é marcada `OVERDUE` corretamente e a notificação de
atraso simplesmente não chega — só sai um `WARN` no log. Vale citar como o
próximo passo, não como bug silencioso descoberto na apresentação.
