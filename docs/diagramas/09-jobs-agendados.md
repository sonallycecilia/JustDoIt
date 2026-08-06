# 9. Jobs agendados — o que o sistema faz sozinho

Três jobs, todos com `@Scheduled` do Spring. Nenhum depende de usuário logado.

| Horário | Job | Serviço | Cron |
|---|---|---|---|
| 00:30, diário | `CycleInstanceJob` | task-service | `0 30 0 * * *` |
| todas as horas, aos :15 | `OverdueTaskJob` | task-service | `0 15 * * * *` |
| 03:00, diário | `RefreshTokenCleanupJob` | auth-service | `0 0 3 * * *` |

```mermaid
flowchart LR
    subgraph t["task-service"]
        j1["<b>CycleInstanceJob</b><br/>00:30 diário<br/><small>rola a janela das séries cíclicas</small>"]
        j2["<b>OverdueTaskJob</b><br/>de hora em hora, aos :15<br/><small>marca atrasadas e notifica</small>"]
    end
    subgraph a["auth-service"]
        j3["<b>RefreshTokenCleanupJob</b><br/>03:00 diário<br/><small>purga tokens expirados</small>"]
    end

    classDef job fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class j1,j2,j3 job
```

## 9.1 OverdueTaskJob — `0 15 * * * *`, de hora em hora

```mermaid
flowchart TD
    tick(["a cada hora, aos 15 min"]) --> q["SELECT task<br/>WHERE status IN (PENDING, IN_PROGRESS)<br/>AND due_date menor que hoje"]
    q --> empty{"achou alguma?"}
    empty -- não --> nada(["retorna"])
    empty -- sim --> mark["UPDATE status = OVERDUE em lote"]
    mark --> log["log.info com a quantidade"]
    log --> loop["para cada tarefa:<br/>notificar o atraso"]
    loop --> tok{"INTERNAL_API_TOKEN<br/>está configurado?"}
    tok -- não --> skip["log.debug e desiste<br/><small>a marcação de OVERDUE já aconteceu</small>"]
    tok -- sim --> post["POST /internal/notifications<br/>X-Internal-Token: segredo"]
    post --> falha["<b>hoje: 404</b> — o endpoint não existe<br/>no notification-service"]
    falha --> warn["log.warn e engole<br/><small>best-effort</small>"]

    classDef job fill:#fff3e0,stroke:#ef6c00,color:#e65100
    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef gap fill:#ffebee,stroke:#c62828,color:#b71c1c
    class tick job
    class mark,log ok
    class falha,warn gap
```

Três decisões aqui:

- **O corte é por dia, não por hora.** `due_date < hoje` — a tarefa tem o dia
  inteiro do prazo como graça, e `due_time` é ignorado de propósito.
- **A mudança de status é o controle de duplicidade.** Ao virar `OVERDUE`, a
  tarefa sai do conjunto varrido. Cada tarefa gera **uma** notificação de atraso,
  sem tabela de "já notifiquei".
- **A marcação não depende da notificação.** Marcar `OVERDUE` acontece mesmo com o
  notification-service fora do ar.

> **Pendência conhecida:** `POST /internal/notifications` ainda não está
> implementado no notification-service. Hoje o atraso é marcado corretamente e a
> notificação não chega — só sai `WARN` no log. Este é o único caso do sistema
> em que a comunicação usa segredo compartilhado em vez do token do usuário,
> justamente porque não há usuário presente para emprestar um token.

## 9.2 CycleInstanceJob — `0 30 0 * * *`, 00:30 diariamente

```mermaid
flowchart LR
    tick(["00:30"]) --> all["findAllWithTask<br/><small>todas as cycle_config, com a task carregada</small>"]
    all --> each["para cada config:<br/>materializer.materialize"]
    each --> idem["idempotente: no-op nas séries<br/>que já têm 4 futuras"]
    idem --> log{"criou alguma?"}
    log -- sim --> info["log.info com o total"]
    log -- não --> quiet(["silêncio"])

    classDef job fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class tick job
```

**Por que varrer tudo é seguro.** O `CycleMaterializer` limita por quantidade e
começa contando o que já existe — séries cheias custam uma contagem e retornam 0.
Detalhes em [07-recorrencia.md](07-recorrencia.md).

## 9.3 RefreshTokenCleanupJob — `0 0 3 * * *`, 03:00 diariamente

```mermaid
flowchart LR
    tick(["03:00"]) --> del["DELETE FROM refresh_token<br/>WHERE expires_at menor que agora"]
    del --> log{"removeu alguma?"}
    log -- sim --> info["log.info com a quantidade"]
    log -- não --> quiet(["silêncio"])

    classDef job fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class tick job
```

**Por que existe.** Sem ele, a tabela `refresh_token` só era limpa quando o
próprio usuário usava o token, logava ou deslogava. As "lápides" — linhas com
`used_at` preenchido, mantidas de propósito para detectar reuso — nunca sairiam
sozinhas de uma conta abandonada. A tabela cresceria indefinidamente.

## Onde os jobs rodam em produção

Cada job é `@Scheduled` **dentro do processo do serviço**. Não há Quartz nem lock
distribuído — o que significa que **com mais de uma réplica do mesmo serviço, o
job rodaria em todas**. Hoje o deploy é de instância única por serviço, então não
é problema; é o ponto a resolver antes de escalar horizontalmente. O mesmo vale
para o `RateLimitFilter`, cujo balde de fichas é local ao processo.
