# 6. Cronômetro — medição de tempo e exclusividade

O tempo é medido **pelo servidor**, nunca pelo cliente. E cada usuário só pode ter
**um** cronômetro rodando por vez.

## 6.1 Start e stop

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant C as TaskTimerController
    participant S as TaskTimerService
    participant AT as active_timer
    participant TT as task_timer

    F->>C: POST /tasks/{id}/timer/start
    C->>S: start(taskId, userId)
    S->>S: findByIdAndUserId — a tarefa é dele?

    S->>AT: findByUserId
    alt já existe cronômetro ativo
        S-->>C: CronometroJaAtivoException
        C-->>F: 409 Conflict
        Note over C: 409 e não 404: o pedido é legítimo,<br/>só chegou depois de outro
    else nenhum ativo
        S->>AT: saveAndFlush {userId, taskId, startedAt = now}
        alt violação do índice único (corrida perdida)
            AT-->>S: DataIntegrityViolationException
            S-->>C: CronometroJaAtivoException
            C-->>F: 409 Conflict
        else inserido
            S-->>F: 200 {id, taskId, startedAt}
        end
    end

    Note over F: ... o usuário trabalha ...

    F->>C: POST /tasks/{id}/timer/stop
    C->>S: stop(taskId, userId)
    S->>AT: findByUserId
    alt nada ativo
        S-->>F: 404 "No active timer"
    else ativo em OUTRA tarefa
        S-->>F: 404 "Active timer belongs to another task"
    else ativo nesta tarefa
        S->>S: decorridos = now menos startedAt<br/><small>calculado no servidor</small>
        S->>AT: DELETE da linha
        S->>TT: UPDATE actual_seconds = actual_seconds + decorridos
        alt a tarefa ainda não tinha task_timer
            S->>TT: INSERT com actual_seconds = decorridos
        end
        S-->>F: 200 TaskTimerResponse
    end
```

## 6.2 Por que o índice único é a garantia, e não o `if`

Este é o ponto que rende pergunta de banca. O `if` antes do insert **não** é a
proteção — é só um atalho para o caso comum.

```mermaid
sequenceDiagram
    participant A as Requisição A
    participant B as Requisição B
    participant S as TaskTimerService
    participant DB as active_timer<br/>(unique em user_id)

    par duas abas do mesmo usuário clicam juntas
        A->>S: start(tarefa1)
    and
        B->>S: start(tarefa2)
    end

    A->>DB: findByUserId → vazio
    B->>DB: findByUserId → vazio
    Note over A,B: as DUAS passaram pelo if.<br/>Aqui o if já falhou como garantia

    A->>DB: saveAndFlush → INSERT ok
    B->>DB: saveAndFlush → viola o índice único
    DB-->>B: DataIntegrityViolationException
    B->>B: traduz para CronometroJaAtivoException
    B-->>B: 409 Conflict

    Note over DB: A EXCLUSIVIDADE VEM DO BANCO.<br/>Nenhuma checagem em Java resolveria:<br/>entre o SELECT e o INSERT cabe outra thread
```

Dois detalhes de implementação que sustentam isso:

- **`saveAndFlush`, não `save`.** Com `save`, a violação só apareceria no commit
  — fora do `try/catch`, virando 500. O `flush` explícito força o erro a
  acontecer dentro do bloco onde é tratado.
- **O rollback é inofensivo.** Esse insert é a única escrita da operação, então
  perder a transação não desfaz nada que importe.

Há evidência visual desse teste de concorrência em
[`docs/img/metrica-3-cronometro-concorrente.png`](../img/metrica-3-cronometro-concorrente.png).

## 6.3 Os três caminhos de escrita de tempo

```mermaid
flowchart TD
    subgraph endp["Endpoints"]
        st["POST /tasks/{id}/timer/start"]
        sp["POST /tasks/{id}/timer/stop"]
        lg["PATCH /tasks/{id}/timer/log<br/><small>soma segundos direto</small>"]
        pu["PUT /tasks/{id}/timer<br/><small>upsert de estimativa</small>"]
        ac["GET /timers/active<br/><small>o que está em curso agora</small>"]
    end

    st --> atv[("active_timer<br/><small>contagem em curso</small>")]
    ac --> atv
    sp --> atv
    sp --> som["somarSegundos"]
    lg --> som
    som --> upd{"UPDATE actual_seconds += n<br/>afetou alguma linha?"}
    upd -- "não, primeiro registro" --> ins["INSERT task_timer"]
    upd -- "sim" --> done(["pronto"])
    ins --> done
    pu --> tt[("task_timer<br/><small>acumulado por tarefa</small>")]
    som --> tt

    classDef ep fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef tab fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    class st,sp,lg,pu,ac ep
    class atv,tt tab
```

**Por que `UPDATE ... += n` em vez de ler-somar-salvar.** O incremento é uma única
instrução SQL atômica. Ler o valor em Java, somar e salvar abriria uma condição de
corrida onde dois logs simultâneos perderiam um dos dois. O `INSERT` só acontece
quando o `UPDATE` não afeta linha nenhuma — ou seja, é o primeiro log de tempo
daquela tarefa. Isso dispensa o frontend de fazer um `PUT` prévio para criar o
timer.

**`GET /timers/active` existe para sobreviver ao F5.** Como a contagem vive no
servidor, recarregar a página não perde o cronômetro: o frontend pergunta o que
está ativo e recalcula o tempo decorrido a partir do `startedAt`.
