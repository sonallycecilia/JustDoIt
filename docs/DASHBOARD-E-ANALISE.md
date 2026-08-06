# Dashboard e Análise de Dados — JustDoIt

Regras de negócio de tudo que produz número na tela: o agregado por período do
task-service, o plano/resumo semanal do schedule-service, os cálculos que sobram
no cliente e a exportação de dados.

Serve como referência para quem for mexer em qualquer um desses pontos: cada
grandeza aqui tem uma definição única, e quase toda divergência histórica nesta
área veio de somar duas coisas que pareciam a mesma.

**Serviços envolvidos:** `task-service` (8081, dono dos dados de execução),
`schedule-service` (8082, dono da agenda e do histórico semanal),
frontend React (`justdoit-frontend`, telas *Visão Geral* e *Análise*).

---

## 1. As quatro grandezas

Este é o vocabulário do domínio. Nenhuma delas substitui a outra, e é proposital
que existam separadas.

| Grandeza | Significado | Fonte | Unidade guardada |
|---|---|---|---|
| **Agendado** | O que ganhou horário marcado no calendário | `time_block` (schedule-service) | minutos |
| **Estimado** | O que o usuário estimou nas tarefas | `task_timer.estimated_minutes` (task-service) | minutos |
| **Executado** | O que foi de fato trabalhado | `focus_session` + `time_entry` (task-service) | segundos |
| **Concluídas** | Tarefas marcadas como feitas | `task.completed_at` / `task.due_date` | contagem |

Relações que a interface explora:

- **Estimado − Agendado** = o que tem duração definida mas nunca entrou na
  agenda ("ainda não tem horário marcado").
- **Executado − Agendado** = `deviationSeconds` do resumo semanal. É o desvio
  contra o **compromisso** assumido no calendário.
- **Executado / Estimado** = o percentual do card "Tempo executado" da Visão
  Geral. Ali a referência é a estimativa, porque a tela é sobre tarefas e
  estimativa existe mesmo para quem não usa a agenda.

Por que executado é em **segundos** e o resto em minutos: cronômetro e Pomodoro
produzem durações com granularidade de segundo; agenda e estimativa são sempre
digitadas em minutos. Converter na origem perderia dado.

---

## 2. Onde mora cada dado

### 2.1 Estimativa da tarefa — `TaskEstimates`

A estimativa que o usuário edita fica no **módulo de cronômetro**
(`PUT /tasks/{id}/timer` → `TaskTimer.estimatedMinutes`). A coluna
`Task.estimatedMinutes` é legada: nem `createTask` nem `updateTask` escrevem
nela.

`TaskEstimates` (`feature/task/TaskEstimates.java`) centraliza a regra:

```java
minutesOf(task)   // timer.estimatedMinutes, ou task.estimatedMinutes (fallback legado), ou null
minutesOrZero(task) // idem, com 0 no lugar de null — para somatórios
```

**Regra:** três caminhos precisam concordar e todos usam este utilitário —
`TaskResponse` (lista de tarefas), `/tasks/report` (relatório) e `/me/export`
(exportação). Enquanto a regra estava duplicada, `TaskService.toResponse` lia a
coluna legada e devolvia `null` para toda tarefa criada pelo app: o "planejado"
do dashboard nascia zerado.

> Quem chama precisa ter o timer carregado (`left join fetch t.timer`) ou estar
> dentro da transação, senão o acesso lazy vira N+1.

### 2.2 Tempo executado — duas origens datadas

| Entidade | O que registra | Como nasce |
|---|---|---|
| `FocusSession` | Ciclo de Pomodoro | `POST /tasks/{id}/focus-sessions` |
| `TimeEntry` | Intervalo de cronômetro | `stop`, `log` ou `PUT /timer` |

`TaskTimer.actualSeconds` é o **acumulado sem data** da tarefa ("esta tarefa já
consumiu 3h"). Nenhum recorte por período consegue enxergá-lo — não dá para saber
se as 3h foram hoje ou no mês passado. Por isso existe `TimeEntry`, um registro
por intervalo, **com data**.

**Regra de consistência:** o acumulado continua sendo a fonte do número exibido
na tarefa; as linhas de `TimeEntry` são a fonte do recorte por dia. Quem escreve
nos dois é sempre o `TaskTimerService`, junto, para que não divirjam.

Como cada operação do cronômetro se comporta:

| Operação | Efeito no acumulado | Efeito nos intervalos datados |
|---|---|---|
| `POST /timer/start` | — | cria `active_timer` (índice único por usuário) |
| `POST /timer/stop` | soma o decorrido | grava `TimeEntry` com as datas **reais** do servidor |
| `PATCH /timer/log` | soma os segundos | grava `TimeEntry` assumindo que terminou **agora** |
| `PUT /timer` (`actualSeconds`) | **define** o total | **apaga** os intervalos da tarefa e regrava um só |

O `PUT` define em vez de incrementar porque é o "zerar cronômetro" e a gravação
de tempo rodado antes de a tarefa existir. Reescrever os intervalos é o que
impede o acumulado e o `/tasks/report` de contarem coisas diferentes.

### 2.3 Agenda — `TimeBlock`

Bloco do calendário, com `date`, início, fim e `estimatedMinutes`. O frontend
grava em `estimatedMinutes` exatamente a duração do bloco (`fim − ini`), então o
"agendado" calculado no cliente bate com o `totalScheduledMinutes` do resumo.

---

## 3. Agregado por período — `GET /tasks/report`

`TaskReportService` é o coração da análise. Uma requisição devolve tudo que a
semana precisa.

**Contrato:** `GET /tasks/report?from=YYYY-MM-DD&to=YYYY-MM-DD`, autenticado
como qualquer outro endpoint (o usuário é o dono do token; não há parâmetro de
`userId`).

### 3.1 Validações

| Regra | Resposta |
|---|---|
| `from` ou `to` ausente | `400` |
| `to` anterior a `from` | `400` |
| Período > **92 dias** (`MAX_RANGE_DAYS`) | `400` |

O teto existe porque o consumidor real pede semanas; um range gigante viraria
varredura da base inteira do usuário.

### 3.2 Semântica de cada campo

| Campo | Base de cálculo |
|---|---|
| `totalTasks` | tarefas cujo **`dueDate`** cai no período |
| `completedTasks` | tarefas cujo **`completedAt`** cai no período, independentemente do `dueDate` |
| `totalActualSeconds` | soma de `focusSeconds + timerSeconds` de todos os dias |
| `totalEstimatedMinutes` | soma das estimativas, atribuída ao **`dueDate`** |
| `byDay[]` | um item **por dia do período, inclusive dias zerados** |

Por dia (`DaySummary`): `date`, `actualSeconds`, `focusSeconds`, `timerSeconds`,
`completedTasks`, `focusSessions`, `estimatedMinutes`.

**`totalTasks` e `completedTasks` têm bases diferentes de propósito.** Uma conta
o que vence no período, a outra o que foi concluído nele. Logo `completedTasks >
totalTasks` é possível (concluir hoje algo que vencia semana passada) — por isso
o anel de conclusão da tela **não** usa esses campos (ver §5.2).

### 3.3 Regras de atribuição do tempo

1. **Foco e cronômetro somam.** São formas independentes de registrar trabalho.
   Quem usa as duas ao mesmo tempo na mesma hora conta o tempo duas vezes — e
   isso é literalmente o que pediu ao ligar os dois. `focusSeconds` e
   `timerSeconds` vêm separados para a tela poder dizer de onde veio o tempo.
2. **O dia é o do INÍCIO**, nas duas origens. Um intervalo que atravessa a
   madrugada pertence ao dia em que começou.
3. **Sessão de `BREAK` não conta.** Pausa não é tempo trabalhado, nem em segundos
   nem na contagem de ciclos.
4. **Duração de uma sessão de foco**, nesta ordem:
   - `started → ended` quando os dois existem e `ended > started`;
   - senão, `focusMinutes × 60` se a sessão está marcada como `completed`;
   - senão, **zero** — sessão aberta ou abandonada não conta.
5. **`focusSessions` conta ciclos que renderam tempo** naquele dia. É o "N ciclos
   de Pomodoro" da tela, que não dá para deduzir dos segundos.
6. **Estimativa é atribuída ao `dueDate`**, não ao dia trabalhado. Tarefa **sem
   data não aparece em dia nenhum** (ver §5.3 para o que a tela faz com isso).

### 3.4 Consultas

Todas cortadas por `userId` (o principal injetado pelo filtro JWT):

- `findByUserIdAndDueDateBetweenWithTimer` — `left join fetch t.timer`, porque a
  estimativa mora no timer;
- `findByUserIdAndCompletedAtBetween`;
- `findByTask_UserIdAndStartedAtBetween` em `FocusSessionRepository` e
  `TimeEntryRepository`.

---

## 4. Histórico semanal — schedule-service

O que transforma a Análise de painel ao vivo em **histórico**.

### 4.1 `WeeklyPlan` — a semana

- Chave de negócio: `(userId, weekStartDate)`, com **índice único**
  `uk_weekly_plan_user_week`.
- Status: `OPEN` → `CLOSED` (sem volta).
- `POST /weekly-plans` é **idempotente**: se a semana já tem plano, devolve o
  existente com `200`; `201` só na criação de fato. O frontend chama isso toda
  vez que a Análise carrega — criar um plano por visita encheria o banco de
  semanas duplicadas, cada uma com o seu resumo. O `findOrCreate` resolve o caso
  comum; o índice único garante sob concorrência.
- `GET /weekly-plans?weekStart=` acha o plano **pela data da segunda**, porque é
  o que o frontend conhece: o id só existiria na resposta do POST e se perderia
  no primeiro reload. `404` quando a semana nunca foi aberta.

### 4.2 `WeeklySummary` — o retrato

| Campo | Origem |
|---|---|
| `totalScheduledMinutes` | soma local dos `TimeBlock` da semana (nulo conta 0) |
| `totalEstimatedMinutes` | `/tasks/report` |
| `totalActualSeconds` | `/tasks/report` |
| `completedTasks`, `totalTasks` | `/tasks/report` |
| `deviationSeconds` | `totalActualSeconds − totalScheduledMinutes × 60` |

**Regras de geração** (`POST /weekly-plans/{id}/summary`):

1. **Semana `CLOSED` devolve o retrato congelado** e não recalcula nada.
   Recalcular apagaria justamente o que o fechamento quis preservar.
2. **O resumo é upsert**: recalcular numa semana aberta atualiza a mesma linha.
3. **A chamada ao task-service é best-effort.** Timeouts curtos (conectar 2s,
   ler 3s) porque a chamada roda dentro da requisição do usuário e da transação
   do resumo. Qualquer falha vira `Optional.empty()`, é logada em `warn` e o
   resumo sai só com os dados locais — um task-service fora do ar **não derruba**
   a geração do resumo. Nesse caminho degradado, `totalTasks` recebe a contagem
   de blocos e `deviationSeconds` fica no default `0`.
4. **O token repassado é o do próprio usuário** (header `Authorization` da
   requisição original). Não existe credencial de serviço: o `/tasks/report` é
   autenticado como qualquer outro endpoint.

### 4.3 Fechamento

`PATCH /weekly-plans/{id}/close`:

1. gera o resumo **uma última vez**;
2. só então marca `CLOSED`.

A ordem importa: sem gerar antes, fechar uma semana sem nunca ter aberto a
Análise deixaria um plano fechado com resumo zerado. Fechar uma semana já
fechada é no-op (não regera).

### 4.4 Leitura vs. cálculo

| Verbo | Efeito |
|---|---|
| `POST /weekly-plans/{id}/summary` | calcula/recalcula e **escreve** |
| `GET /weekly-plans/{id}/summary` | **lê o salvo**, sem recalcular; `404` se não existe |

Antes, o GET chamava o gerador — ou seja, escrevia no banco e batia no
task-service **a cada leitura**. Quem quer gerar usa o POST.

---

## 5. Camada de apresentação

### 5.1 Telas

| Tela | Arquivo | O que mostra |
|---|---|---|
| **Visão Geral** | `features/dashboard/pages/VisaoGeral.jsx` | concluídas da semana, tempo de hoje (com origem), executado vs. estimado, tarefas de hoje priorizadas |
| **Análise** | `features/dashboard/pages/Analise.jsx` | gráfico das três séries por dia, tempo por categoria, anel de conclusão, retrato da semana fechada, insights |

Gráficos, todos **SVG puro sem dependência**:
`DeviationChart` (barras agrupadas, 3 séries por dia),
`CategoryChart` (donut por categoria),
`RateRing` (anel de taxa de conclusão).

### 5.2 O hook `useAnaliseSemanal`

Fonte única dos números das duas telas. **Duas requisições no total:**
`GET /tasks/report` da semana + `GET /time-blocks?from=&to=` (esta compartilha a
`queryKey` do calendário, então quem vem de lá já tem cache). Antes eram
**2 por tarefa**, com teto de 60 tarefas.

Semana = **segunda a domingo** (`intervaloSemana`), índice 0–6 com segunda em 0.

O que é calculado no cliente, e por quê:

| Cálculo | Motivo de não vir do backend |
|---|---|
| **Tempo por categoria** | nenhum endpoint agrega por categoria |
| **Taxa de conclusão** | `completedTasks`/`totalTasks` do relatório têm bases diferentes e o anel passaria de 100%; a tela promete "tarefas desta semana", que é a conta local sobre a lista já carregada |
| **Agendado por dia** | vem dos blocos do calendário (outro serviço) |

Ambos saem de dados que a página já tem: custo zero de rede.

### 5.3 Tarefa sem data

Tarefa com estimativa e **sem** `dueDate` não é vista pelo relatório, que agrega
por `dueDate`. Regra da interface:

- **entra no total estimado** — o tempo que a pessoa planeja gastar existe;
- **fica fora das séries por dia** — não há dia onde pendurá-la, e inventar um
  mentiria no gráfico;
- a tela **diz isso explicitamente** (`estimadoSemData`, `tarefasSemData`), senão
  o total pareceria não bater com a soma das barras.

Os dois conjuntos (com data / sem data) são disjuntos, então não há risco de
contar a mesma tarefa duas vezes.

### 5.4 Estados de tela

`pronto = !carregando && !erro` é o **único** estado em que um card pode afirmar
algo sobre a semana. Carregando ainda não sabe; com erro, os zeros não
significam "vazio" — uma requisição que falha silenciosamente produziria uma tela
idêntica à de uma semana sem dados, que é o pior tipo de erro. Por isso o hook
expõe `erro` e `recarregar`, e a tela traduz o status (`404` = serviço
desatualizado/parado, `401/403` = sessão).

### 5.5 Insights

Frases geradas a partir dos números da semana, em `montarInsights`:

| Condição | Frase |
|---|---|
| há tarefas na semana | "concluiu X de Y (Z%)" — tom muda em **60%** |
| `totalAgendado > 0` | executado vs. agendado, com o excedente/faltante |
| `totalEstimado − totalAgendado > 0.25h` | quanto ainda não tem horário marcado |
| há categorias | maior fatia do tempo estimado |

### 5.6 Ciclo de vida na tela

Semana **aberta** → números ao vivo, recalculados a cada visita.
Semana **fechada** → aparece o card "Retrato da semana fechada", lido do resumo
salvo, com o aviso de que não muda mais. É assim que uma semana antiga deixa de
depender do estado **atual** das tarefas.

O botão "Fechar semana" pede confirmação, abre o plano se ainda não existir (o
backend devolve o existente sem duplicar) e então fecha.

---

## 6. Exportação de dados — `GET /me/export`

Portabilidade/backup, em `format=csv` ou `format=json` (formato inválido →
`400`).

Uma linha por tarefa, com: `id`, `title`, `status`, `completed`, `categoryName`,
`priority`, `dueDate`, `createdAt`, `completedAt`, `estimatedSeconds`,
`actualSeconds`, `note`.

Regras:

- `estimatedSeconds` vem de `TaskEstimates.minutesOf × 60` — a mesma estimativa
  do dashboard;
- `actualSeconds` vem do **acumulado** `TaskTimer.actualSeconds` (0 quando não
  há timer), não da soma dos intervalos;
- CSV segue a **RFC 4180** (`CRLF`, aspas só quando necessário, aspas internas
  duplicadas) e leva **BOM UTF-8**: o Excel abre CSV sem BOM na página de código
  local e estraga os acentos, e o app é todo em português;
- conta sem tarefas gera arquivo válido com só o cabeçalho;
- **fórmulas de planilha (`=`, `+`, `@`) não são neutralizadas**: o arquivo tem
  apenas o que o próprio usuário digitou e é aberto por ele mesmo; prefixar
  valores corromperia o backup, que é o propósito do arquivo;
- nome: `export_tarefas_AAAA-MM-DD.csv|json`.

---

## 7. Segurança e isolamento

- **Todo corte é por `userId`**, sempre o principal injetado pelo filtro JWT.
  Nenhum endpoint desta área aceita `userId` do cliente — não há como pedir o
  relatório, o resumo ou a exportação de outra conta.
- Recursos do schedule-service são buscados por `findByIdAndUserId`; plano de
  outro dono responde `404`, não `403`.
- `/tasks/report` **não tem credencial de serviço**. O schedule-service repassa o
  token do próprio usuário, então a autorização é a mesma de uma chamada direta
  do app.
- O teto de 92 dias limita o custo de uma requisição autenticada (§3.1).

---

## 8. Endpoints

| Método | Rota | Serviço | Papel |
|---|---|---|---|
| `GET` | `/tasks/report?from=&to=` | task | agregado do período (§3) |
| `GET` | `/me/export?format=` | task | exportação (§6) |
| `GET` | `/tasks/{id}/timer` · `PUT` | task | estimativa e acumulado |
| `PATCH` | `/tasks/{id}/timer/log` | task | log manual de segundos |
| `POST` | `/tasks/{id}/timer/start` · `/stop` | task | cronômetro |
| `GET` | `/timers/active` | task | cronômetro em curso do usuário |
| `GET`/`POST` | `/tasks/{id}/focus-sessions` | task | ciclos de Pomodoro |
| `GET` | `/time-blocks?from=&to=` (ou `?date=`) | schedule | agenda (§2.3) |
| `POST` | `/weekly-plans` | schedule | abre a semana (idempotente) |
| `GET` | `/weekly-plans?weekStart=` | schedule | acha o plano pela segunda |
| `PATCH` | `/weekly-plans/{id}/close` | schedule | congela o retrato |
| `POST` | `/weekly-plans/{id}/summary` | schedule | calcula/recalcula |
| `GET` | `/weekly-plans/{id}/summary` | schedule | lê o salvo |

---

## 9. Métrica de qualidade e testes

### 9.1 Taxa de Fidelidade do Tempo Executado

**Atributo:** correção funcional.
**Teste:** `task-service/src/test/java/com/justdoit/task/qualidade/TempoExecutadoMetricsTest.java`

```
X = A / B          (0 ≤ X ≤ 1; ideal = 1)
A = segundos de trabalho que o relatório atribui ao DIA CORRETO
B = total de segundos de trabalho registrados no período
```

- **Unidade é segundo, não requisição.** As outras métricas do projeto contam
  requisições porque medem bloqueio (passou/não passou). Aqui o defeito é
  quantitativo: um relatório que devolve metade do tempo responde `200` e parece
  saudável.
- **Crédito por dia, sem meio-termo.** Um dia só soma para `A` se o total bater
  exatamente. Isso também mantém `X ≤ 1` sob dupla contagem.
- **Dia com zero registrado entra na verificação, não no denominador**: tempo que
  aparece do nada é cobrado na lista de falhas, que reprova o teste sozinha.

O que a métrica protege: o card "Tempo executado" afirma **quanto** a pessoa
trabalhou e o gráfico afirma **em que dia**. `X < 1` significa que o produto está
mentindo sobre o esforço dela — e os números continuam plausíveis.

A semana medida é fixa no passado (29/06/2026–05/07/2026) e reúne os casos que já
falharam de verdade: foco virando a madrugada, dia só de cronômetro, cronômetro
atravessando a meia-noite, as duas origens no mesmo dia, pausa e ciclo abandonado
como ruído, e dois dias que precisam devolver zero. Medida antes de `TimeEntry`
existir (05/08/2026), daria bem abaixo de 1: todo tempo de cronômetro era
invisível para qualquer recorte por período.

### 9.2 Cobertura por regra

`TaskReportServiceTest` — agregação por dia com dias zerados · pausa não conta ·
período invertido e acima do teto · `from == to` · cronômetro somado ao foco e
separável dele · intervalo atravessando a madrugada · estimativa vinda do timer
com a coluna legada como fallback.

`TaskReportControllerTest` — agregados no JSON · range inválido → `400` ·
parâmetros ausentes → `400`.

`ScheduleServiceTest` / `ScheduleControllerTest` — semana já aberta não duplica
(`200` vs `201`) · fechar marca `CLOSED` · semana já fechada não regera ·
resumo sem relatório usa só dados locais · resumo com relatório preenche os
reais · semana fechada devolve o congelado · `GET` do resumo lê sem recalcular ·
`404` quando a semana não foi aberta/resumida.

Frontend — `useAnalytics.test.jsx`, `useWeeklyPlan.test.jsx`,
`VisaoGeral.test.jsx`.

---

## 10. Limitações conhecidas

| Limitação | Consequência |
|---|---|
| Nenhum endpoint agrega tempo por categoria | o donut usa tempo **estimado**, não executado, e é calculado no cliente |
| `/tasks/report` agrega estimativa por `dueDate` | tarefa sem data só entra no total, via cliente (§5.3) |
| Foco e cronômetro simultâneos somam | usar os dois na mesma hora conta o tempo duas vezes — decisão explícita |
| `deviationSeconds` fica `0` no caminho degradado | resumo gerado sem o task-service não distingue "sem desvio" de "sem dado" |
| A análise cobre só a semana corrente | não há navegação por semanas anteriores na tela; o histórico existe no backend (planos fechados) |
| Exportação usa o acumulado do timer | `actualSeconds` do arquivo pode divergir da soma dos intervalos se algo escrever fora do `TaskTimerService` |

---

## 11. Mapa de arquivos

**task-service**
```
feature/report/TaskReportController.java     rota, validação → 400
feature/report/TaskReportService.java        agregação por dia (§3)
feature/task/TaskEstimates.java              regra única da estimativa (§2.1)
feature/timer/TimeEntry.java                 intervalo datado (§2.2)
feature/timer/TaskTimerService.java          start/stop/log/upsert
feature/focussession/FocusSession*.java      ciclos de Pomodoro
feature/export/TaskExport*.java              exportação (§6)
shared/TaskReportResponse.java               contrato do agregado
```

**schedule-service**
```
feature/schedule/ScheduleService.java        plano, resumo, fechamento (§4)
feature/schedule/WeeklyPlan.java             semana + índice único
feature/schedule/WeeklySummary.java          retrato
integration/TaskReportClient.java            chamada best-effort
shared/WeeklySummaryResponse.java            contrato do resumo
```

**frontend**
```
features/dashboard/hooks/useAnalytics.js     fonte única dos números (§5.2)
features/dashboard/hooks/useWeeklyPlan.js    plano/resumo/fechamento
features/dashboard/pages/VisaoGeral.jsx      home semanal
features/dashboard/pages/Analise.jsx         análise + insights
features/dashboard/components/*.jsx          gráficos SVG
api/endpoints.js                             rotas por serviço
lib/utils.js                                 intervaloSemana, horas, pct
```
