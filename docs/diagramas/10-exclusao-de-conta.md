# 10. Exclusão de conta — consistência entre dois serviços

Os dados do usuário vivem em **dois serviços**: a identidade no auth-service
(`users`, `refresh_token`) e o conteúdo no task-service (`task`, `category`,
`note` e satélites). Não há FK entre eles nem transação distribuída — a
consistência é orquestrada no código, com ordem deliberada.

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant AC as AuthController<br/>:8080
    participant AS as AuthService
    participant TSC as TaskServiceClient
    participant TDC as UserDataController<br/>:8081
    participant TDS as UserDataService
    participant TDB as MySQL<br/>tabelas do task
    participant ADB as MySQL<br/>tabelas do auth

    F->>AC: DELETE /auth/me<br/>Authorization: Bearer ...
    AC->>AS: deleteAccount(userId, authHeader)
    Note over AC: o header é recebido explicitamente<br/>para ser REPASSADO adiante

    rect rgb(232, 245, 233)
    Note over AS,ADB: tudo dentro de um @Transactional do auth-service
    AS->>ADB: findById em users
    alt usuário não existe
        AS-->>F: 400
    end

    AS->>TSC: deleteUserData(authHeader)
    TSC->>TDC: DELETE /me/data<br/>Authorization: Bearer do próprio usuário
    Note over TDC: rota NÃO exposta no nginx —<br/>é chamada interna auth para task.<br/>Protegida pelo JWT normal: o userId<br/>sai do token, não de parâmetro

    TDC->>TDS: deleteAllForUser(userId)

    alt task-service responde OK
        TDS->>TDB: findByUserId em task, deleteAll<br/><small>deleteAll das entidades, não delete em massa:<br/>carrega cada tarefa e dispara o cascade JPA<br/>em subtarefas, timer, nota, foco e ciclo</small>
        TDS->>TDB: findByUserId em category, deleteAll
        TDS->>TDB: deleteByUserId em note
        TDC-->>TSC: 204
        AS->>ADB: DELETE refresh_token do usuário
        AS->>ADB: DELETE users
        Note over AS,ADB: COMMIT
        AC-->>F: 204 No Content
    else task-service fora do ar, lento ou com erro
        TDC-->>TSC: falha
        TSC-->>AS: exceção propaga
        Note over AS,ADB: ROLLBACK — a conta NÃO é excluída
        AC-->>F: 502 "Não foi possível remover seus<br/>dados de tarefas. Tente novamente."
    end
    end
```

## A ordem importa

```mermaid
flowchart LR
    subgraph certa["Ordem implementada"]
        direction TB
        a1["1. apaga dados no task-service"] --> a2["2. apaga refresh tokens"] --> a3["3. apaga o usuário"]
        a3 --> a4["falha em 1 → rollback,<br/>nada foi perdido:<br/>o usuário tenta de novo"]
    end

    subgraph errada["Ordem invertida — não usada"]
        direction TB
        b1["1. apaga o usuário"] --> b2["2. apaga dados no task-service"]
        b2 --> b3["falha em 2 → dados órfãos,<br/>com user_id de uma conta<br/>que não existe mais.<br/><b>Irrecuperável</b>"]
    end

    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef bad fill:#ffebee,stroke:#c62828,color:#b71c1c
    class a1,a2,a3,a4 ok
    class b1,b2,b3 bad
```

**Apagar o mais frágil primeiro.** A chamada remota é a parte que pode falhar, e
ela é a primeira. Se falhar, a transação local reverte e o estado volta a ser
consistente — o usuário só vê um 502 e pode repetir. Na ordem inversa, uma falha
deixaria dados órfãos permanentes.

**O que essa exclusão *não* apaga.** O notification-service e o schedule-service
não são chamados: `notification`, `notification_preference`, `time_block`,
`weekly_plan` e `weekly_summary` do usuário **permanecem no banco**. Vale citar
como limitação conhecida — a orquestração cobre auth e task, não os quatro
serviços.

## Fluxos relacionados dos dados do usuário

```mermaid
flowchart TD
    subgraph user["O que o usuário pode fazer com os próprios dados"]
        exp["<b>Exportar</b><br/>GET /me/export?format=csv|json<br/><small>Configurações › Dados</small>"]
        del["<b>Excluir a conta</b><br/>DELETE /auth/me"]
    end

    exp --> corte["uma query por user_id<br/><small>o mesmo userId que o JwtAuthFilter injeta.<br/>não existe parâmetro de usuário no request:<br/>não há como pedir a exportação de outra pessoa</small>"]
    corte --> fmt{"format"}
    fmt -- json --> j["TaskExportResponse"]
    fmt -- csv --> c["CSV RFC 4180, com CRLF e BOM<br/><small>o BOM é para o Excel não estragar<br/>os acentos do português</small>"]

    del --> flow["ver o diagrama acima"]

    classDef ep fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef sec fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    class exp,del ep
    class corte sec
```
