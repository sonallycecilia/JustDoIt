# 7. Tarefas cíclicas — materialização das ocorrências

Uma tarefa recorrente não é "uma tarefa que se repete virtualmente": as
ocorrências futuras são **tarefas reais no banco**, com data, para aparecerem no
calendário e na lista como qualquer outra.

## 7.1 O conceito de série

```mermaid
flowchart LR
    subgraph serie["Série de uma tarefa semanal"]
        modelo["<b>MODELO</b><br/>task 'Academia'<br/>due_date = 14/07<br/>series_id = <b>null</b><br/>possui o cycle_config"]
        o1["ocorrência<br/>21/07<br/>series_id = id do modelo"]
        o2["ocorrência<br/>28/07<br/>series_id = id do modelo"]
        o3["ocorrência<br/>04/08<br/>series_id = id do modelo"]
        o4["ocorrência<br/>11/08<br/>series_id = id do modelo"]
    end

    modelo --> o1 --> o2 --> o3 --> o4

    cfg["cycle_config<br/><small>cycle_type = WEEKLY<br/>end_date opcional</small>"] -.-> modelo

    classDef mod fill:#e3f2fd,stroke:#1565c0,color:#0d47a1,stroke-width:2px
    classDef occ fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c
    classDef c fill:#fff8e1,stroke:#ff8f00,color:#e65100
    class modelo mod
    class o1,o2,o3,o4 occ
    class cfg c
```

O modelo **conta como a primeira ocorrência**. As geradas são cópias só dos campos
base — título, descrição, prioridade, categoria, data, hora. Não levam
subtarefas, timer, nota nem `cycle_config`.

## 7.2 Quando a materialização acontece

```mermaid
flowchart TD
    a["<b>Criação da recorrência</b><br/>PUT /tasks/{id}/cycle-config"] --> mat
    b["<b>Job diário 00:30</b><br/>CycleInstanceJob<br/><small>varre TODAS as cycle_config</small>"] --> mat
    mat["CycleMaterializer.materialize"]
    mat --> tipo{"cycle_type"}
    tipo -- "DAILY, WEEKLY, BIWEEKLY,<br/>MONTHLY, ANNUAL" --> preset["materializePreset<br/><small>granularidade de dia,<br/>encerra por end_date</small>"]
    tipo -- CUSTOM --> custom["materializeCustom<br/><small>a cada N horas/dias,<br/>total_occurrences vezes</small>"]

    classDef ent fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef job fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class a ent
    class b job
```

A geração acontece **na criação** — o usuário vê as ocorrências no calendário
imediatamente. O job diário só **rola a janela** conforme os dias passam: repõe as
ocorrências que entraram no horizonte.

## 7.3 Presets — limite por quantidade

```mermaid
flowchart TD
    start(["materializePreset"]) --> count["conta ocorrências futuras PENDING<br/>desta série com due_date maior ou igual a hoje"]
    count --> faltam{"faltam = 4 menos existentes<br/>é maior que zero?"}

    faltam -- não --> noop(["retorna 0 — já há ocorrências suficientes"])
    faltam -- sim --> cursor["cursor = maior due_date da série<br/><small>fallback: due_date do modelo, senão hoje</small>"]
    cursor --> adv["próxima = cycle_type.advance(cursor)<br/><small>DAILY +1d · WEEKLY +7d · BIWEEKLY +14d<br/>MONTHLY +1 mês · ANNUAL +1 ano</small>"]

    adv --> loop{"criadas menor que faltam<br/>E próxima não passou de end_date<br/>E guarda menor que 500?"}
    loop -- não --> fim
    loop -- sim --> passada{"próxima é anterior a hoje?"}
    passada -- "sim — pula" --> next
    passada -- não --> ins["INSERT ocorrência"] --> next
    next["próxima = advance(próxima)"] --> loop

    fim["grava next_reset_date<br/><small>null se passou do end_date = série encerrada</small>"] --> ret(["retorna quantas criou"])

    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef dec fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class ins,ret,fim ok
    class faltam,loop,passada dec
```

**O limite é por quantidade — no máximo 4 futuras por série — e não por dias.**
Isso é deliberado: com limite por janela de tempo, "diário" geraria 30 tarefas e
"anual" nenhuma. Por quantidade, diário, semanal e quinzenal geram sempre o mesmo
número pequeno, e nunca há enxurrada.

**A contagem é o que torna a operação idempotente.** Chamar `materialize` de novo
— na criação, ao re-salvar a config, no job diário — não passa do limite, porque a
primeira coisa que ele faz é contar o que já existe. Não há tabela de controle nem
flag de "já materializado".

**`MAX_ITERACOES = 500`** é trava contra laço patológico: uma série com cursor
muito antigo teria que "alcançar" hoje avançando de 1 em 1 dia.

## 7.4 CUSTOM — a cada N horas ou dias

```mermaid
flowchart TD
    start(["materializeCustom"]) --> valid{"interval_unit, interval_count > 0<br/>e total_occurrences > 1?"}
    valid -- não --> zero(["retorna 0 — config incompleto"])
    valid -- sim --> anchor["âncora = start_date/start_time do config<br/><small>fallback: due_date/due_time do modelo, senão hoje</small>"]
    anchor --> janela{"faltam = 4 menos futuras existentes<br/>é maior que zero?"}
    janela -- não --> zero2(["retorna 0"])
    janela -- sim --> k["para k de 1 até total_occurrences menos 1"]
    k --> calc["dt = âncora + k vezes intervalo<br/><small>HOURS → plusHours · DAYS → plusDays</small>"]
    calc --> past{"dt já passou?"}
    past -- sim --> k
    past -- não --> exists{"existsOccurrence(série, data, hora)?"}
    exists -- "sim — já materializada" --> k
    exists -- não --> ins["INSERT ocorrência"] --> cheia{"já criou 'faltam'?"}
    cheia -- não --> k
    cheia -- sim --> fim["grava next_reset_date"] --> ret(["retorna quantas criou"])

    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef dec fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class ins,ret,fim ok
    class valid,janela,past,exists,cheia dec
```

Diferenças em relação aos presets:

- A posição da ocorrência *k* é calculada **da âncora**, não do cursor anterior —
  `âncora + k * intervalo`. Isso permite pular ocorrências já passadas sem
  desalinhar as seguintes.
- A idempotência vem de `existsOccurrence(série, data, hora)`, não da contagem —
  precisa ser por data+hora porque `HOURS` gera várias ocorrências no mesmo dia.
- `total_occurrences` tem teto de **365** (validado no service), e o laço é
  limitado por ele.

## 7.5 Remover a recorrência

```mermaid
flowchart LR
    del(["DELETE /tasks/{id}/cycle-config"]) --> limpa["DELETE ocorrências da série<br/>que são PENDING <b>e</b> com due_date maior ou igual a hoje"]
    limpa --> cfg["DELETE cycle_config"]
    cfg --> ok(["204"])

    limpa -.-> preserva["<b>Preserva</b>: ocorrências já concluídas<br/>e ocorrências passadas<br/><small>o histórico não é reescrito</small>"]

    classDef ok2 fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef keep fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c
    class limpa,cfg,ok ok2
    class preserva keep
```
