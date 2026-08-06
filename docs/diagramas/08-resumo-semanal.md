# 8. Resumo semanal — planejado versus executado

Este é o único fluxo que **cruza dois serviços para produzir um dado novo**. O
schedule-service sabe o que foi *planejado* (blocos de tempo); o task-service sabe
o que foi *executado* (tarefas concluídas e sessões de foco). O resumo é o
encontro dos dois.

## 8.1 O fluxo

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant SC as ScheduleController
    participant SS as ScheduleService
    participant SDB as MySQL<br/>tabelas do schedule
    participant TRC as TaskReportClient
    participant TS as task-service<br/>:8081

    F->>SC: POST /weekly-plans/{id}/summary<br/>Authorization: Bearer ...
    SC->>SS: generateWeeklySummary(planId, userId, authHeader)

    SS->>SDB: findByIdAndUserId no weekly_plan
    alt não é do usuário ou não existe
        SS-->>F: 404
    end

    SS->>SDB: SELECT time_block WHERE user_id<br/>AND date BETWEEN início e fim da semana
    SS->>SS: totalEstimated = soma dos estimated_minutes<br/><small>o PLANEJADO — 100% local</small>

    SS->>TRC: getReport(authHeader, weekStart, weekEnd)
    TRC->>TS: GET /tasks/report?from=...&to=...<br/>Authorization: Bearer do usuário<br/><small>connect 2 s / read 3 s</small>

    alt task-service responde
        TS-->>TRC: {totalTasks, completedTasks, totalActualSeconds, byDay}
        TRC-->>SS: Optional com o relatório
        SS->>SS: totalTasks e completedTasks do relatório<br/>totalActualSeconds do relatório<br/><b>deviationSeconds = executado menos planejado em segundos</b>
    else fora do ar, lento ou erro
        TS-->>TRC: falha
        TRC->>TRC: log.warn e devolve Optional.empty
        SS->>SS: degrada: totalTasks = quantidade de blocos,<br/>executado e desvio ficam zerados
        Note over SS: o resumo SAI, só sem os dados reais.<br/>Um task-service fora do ar não<br/>derruba a geração do resumo
    end

    SS->>SDB: upsert weekly_summary<br/><small>findByWeeklyPlanId ou cria — regerar sobrescreve</small>
    SS-->>F: 200 WeeklySummaryResponse
```

`GET /weekly-plans/{id}/summary` chama **o mesmo método** de `POST`: consultar o
resumo é sempre regerá-lo com dados frescos, em vez de devolver um snapshot velho.

## 8.2 Como o task-service calcula o relatório

```mermaid
flowchart TD
    req(["GET /tasks/report?from=&to="]) --> val{"from e to preenchidos<br/>e from menor ou igual a to?"}
    val -- não --> e400["400 'Período inválido'"]
    val -- sim --> range{"período menor ou igual<br/>a 92 dias?"}
    range -- não --> e400b["400 'Período máximo de 92 dias'"]
    range -- sim --> calc

    subgraph calc["Agregação por dia"]
        direction TB
        q1["<b>totalTasks</b><br/>COUNT task WHERE user_id<br/>AND due_date BETWEEN from e to"]
        q2["<b>concluídas</b><br/>SELECT task WHERE user_id<br/>AND completed_at BETWEEN from e to+1<br/><small>agrupa por dia de completed_at</small>"]
        q3["<b>tempo executado</b><br/>SELECT focus_session WHERE task.user_id<br/>AND started_at no intervalo<br/><small>agrupa por dia de started_at</small>"]
    end

    calc --> dur["duração de cada sessão de foco"]
    dur --> d1{"tem started_at E ended_at,<br/>com ended depois de started?"}
    d1 -- sim --> u1["usa o intervalo real"]
    d1 -- não --> d2{"completed = true<br/>e focus_minutes preenchido?"}
    d2 -- sim --> u2["usa focus_minutes vezes 60"]
    d2 -- não --> u3["conta ZERO<br/><small>sessão aberta ou abandonada<br/>não vira tempo trabalhado</small>"]

    u1 --> fill
    u2 --> fill
    u3 --> fill
    fill["preenche TODOS os dias do intervalo,<br/>inclusive os vazios com 0"] --> resp(["TaskReportResponse<br/>{from, to, totalTasks, completedTasks,<br/>totalActualSeconds, byDay[]}"])

    classDef bad fill:#ffebee,stroke:#c62828,color:#b71c1c
    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    class e400,e400b,u3 bad
    class resp,fill ok
```

## 8.3 Por que o desenho é esse

**O teto de 92 dias.** O consumidor real pede uma semana. Sem teto, um `from`
antigo viraria varredura da base inteira do usuário — é limite de segurança de
performance, não regra de negócio.

**`/tasks/report` é um endpoint normal, não interno.** É autenticado com o token
do usuário como qualquer outro. O `TaskReportClient` só repassa o header que já
chegou. Nenhuma credencial especial, nenhuma rota privilegiada.

**A degradação é assimétrica de propósito.** O dado *planejado* é local e nunca
falha. O dado *executado* é remoto e pode faltar. O resumo prefere sair
incompleto a não sair: o usuário vê o planejado e as métricas reais aparecem na
próxima geração, quando o task-service responder.

**Unidades diferentes nos dois lados.** O planejado é em **minutos** (o usuário
estima em minutos ao criar o bloco); o executado é em **segundos** (o cronômetro
mede em segundos). O desvio é calculado em segundos —
`totalActualSeconds - totalEstimated * 60`.
