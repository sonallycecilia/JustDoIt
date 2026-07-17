# Plano — Integração do schedule-service e notification-service

> **Data:** 2026-07-03
> **Decisões acordadas:** comunicação REST síncrona com repasse do token do usuário
> (padrão `TaskServiceClient` já existente no auth-service); jobs em background já
> nesta fase; entrega de notificações ao front por polling.

## 1. O problema

Os dois serviços já têm CRUD e testes, mas são ilhas — só o front os chama:

- `WeeklySummary` tem `totalActualSeconds`, `deviationSeconds` e `completedTasks`
  **sempre null**: os dados vivem no task-service (`TaskTimer.actualSeconds`,
  `FocusSession`, `TaskStatus.COMPLETED`).
- As preferências `notifyOnComplete/Overdue/CycleReset` não têm produtor: hoje só o
  front pode criar notificações (`POST /notifications`). Com a aba fechada nada nasce,
  e *overdue* não tem gatilho nenhum.
- No front: `blocos.js` já consome `/time-blocks`; o `dashboard.js` tem um TODO
  esperando `GET /analytics/weekly` (calcula stats localmente enquanto isso); não
  existe UI de notificações.
- Lacunas standalone do schedule: `overlaps()` existe mas nunca é chamado;
  `TimeBlock.taskId` não é validado; não há `GET /weekly-plans`.

## 2. Arquitetura de comunicação

Dois modos, conforme haja ou não um usuário na ponta:

```
COM usuário (request do front):
  front ──token──▶ schedule ──mesmo token──▶ task-service
  front ──token──▶ task ─────mesmo token──▶ notification (evento "concluiu tarefa")

SEM usuário (job @Scheduled):
  task-service (job de overdue/ciclo)
      └──X-Internal-Token──▶ notification-service /internal/notifications
```

- **Token do usuário repassado:** o serviço B valida o mesmo JWT (mesmo segredo +
  `iss`/`aud`/`type` já exigidos). Sem credencial nova, sem IDOR — o `userId` continua
  vindo do token.
- **Endpoint interno para jobs:** rota `/internal/**` protegida por segredo de ambiente
  (`INTERNAL_API_TOKEN`, comparado em filtro dedicado) e **não roteada no nginx**
  (defesa em profundidade: segredo + inacessível de fora). Não é um JWT — de propósito:
  não passa nos filtros de access token.
- **Política de falha:** notificação é *best-effort* (try/catch + log; nunca derruba a
  operação principal). Já a chamada schedule→task no resumo semanal é essencial: falhou,
  retorna 502 ao front.

## 3. Fases

### Fase 1 — task-service expõe o que os outros precisam ✅ (implementada em 2026-07-04)

> **Notas da implementação:**
> - `Task.completedAt` foi adicionado (setado no complete, limpo no reopen) — `updatedAt`
>   não servia para "concluídas no período" porque muda em qualquer edição.
> - A notificação de conclusão dispara via `TaskCompletedEvent` +
>   `@TransactionalEventListener(AFTER_COMMIT)` — rollback não gera notificação falsa.
> - **CYCLE_RESET foi adiado:** o backend não tem gatilho de reset de ciclo (a lógica
>   vive no front, `cycle.js`); notificar exigiria antes mover essa regra para o servidor.
> - **Dependência da Fase 4:** o front hoje guarda os logs de tempo em localStorage
>   (`FOCO_DIARIO`/`TEMPO_DIARIO`) e não usa os endpoints de FocusSession — até migrar,
>   o `actualSeconds` do report virá zerado para usuários reais.

1. **`GET /tasks/report?from=&to=`** (autenticado, dados do próprio usuário):
   agregado do período para o schedule:
   ```json
   {
     "totalTasks": 12, "completedTasks": 8, "totalActualSeconds": 43200,
     "byDay": [ { "date": "2026-07-01", "actualSeconds": 7200, "completedTasks": 2 } ]
   }
   ```
   Fontes: `TaskTimer.actualSeconds`, `FocusSession` (started/ended), `Task.status/dueDate`.
2. **`NotificationClient`** (RestClient, espelho do `TaskServiceClient` do auth) +
   gatilho ao concluir tarefa → cria notificação `TASK_COMPLETED` repassando o token
   (best-effort). Reset de ciclo disparado por request do usuário → `CYCLE_RESET`.
3. **Job `@Scheduled`** (horário): marca `OVERDUE` tarefas com `dueDate` vencida e
   status `PENDING/IN_PROGRESS`, e notifica via endpoint interno. (`@EnableScheduling`
   no task-service, como já feito no auth.)

### Fase 2 — notification-service vira consumidor real de eventos

1. **Checagem de preferência dentro do serviço:** `createNotification` consulta
   `NotificationPreference` e descarta silenciosamente o tipo desativado (hoje ninguém
   checa — a decisão não pode ficar com o chamador).
2. **`POST /internal/notifications`**: recebe `userId` no corpo; filtro dedicado valida
   `X-Internal-Token`; rota fora do nginx. Documentar em `docs/security.md`.
3. **Dedup de OVERDUE:** não criar duplicada para a mesma task enquanto a anterior não
   for lida (consulta por `taskId+type+read=false`).

### Fase 3 — schedule-service: fechar o domínio + integração

1. Standalone: validar sobreposição de blocos usando o `overlaps()` (409 Conflict),
   `start < end`, coerência `date`/`startDateTime`; **`GET /weekly-plans`** (falta
   qualquer leitura de plano!); unicidade `userId + weekStartDate`.
2. **`TaskServiceClient` no schedule:** `generateWeeklySummary` chama
   `GET /tasks/report` com o token do usuário e preenche `totalActualSeconds`,
   `completedTasks` e `deviationSeconds = totalActualSeconds − (estimado dos blocos × 60)`.
3. **`GET /analytics/weekly`** — o endpoint que o front já espera (nginx já roteia
   `/analytics` → 8082). Contrato conforme `dashboard.js#atualizarStats`:
   ```json
   {
     "conclusao": { "feitas": 8, "total": 12 },
     "desvio": [ { "plan": 4.0, "real": 3.5 } ]   // por dia da semana, em HORAS
   }
   ```
   `plan` = estimado dos time-blocks do dia; `real` = actualSeconds/3600 do report.
4. Validação de `taskId` do TimeBlock na criação (GET /tasks/{id} com o token; 400 se
   não existir/não for do usuário).

### Fase 4 — frontend (repo `justdoit-frontend`)

1. **Notificações:** badge + lista (marcar como lida), polling
   `GET /notifications/unread` a cada 60s; tela de preferências
   (`GET/PUT /notifications/preferences`). SSE fica como evolução futura se o polling
   incomodar.
2. **Dashboard:** reativar o TODO — `Api.get(Api.endpoints.analytics.weekly).then(atualizarStats)`
   com fallback no cálculo local em caso de erro.
3. **Plano semanal:** UI de criar/fechar semana e ver o resumo (usa os endpoints da Fase 3).
4. **Persistir logs de tempo no backend:** trocar o localStorage (`FOCO_DIARIO`/
   `TEMPO_DIARIO`) por `POST /tasks/{id}/sessions` (FocusSession) — sem isso o
   "tempo executado" do report/analytics fica zerado (ver nota da Fase 1).

## 4. Transversal

- **Env novas:** `INTERNAL_API_TOKEN` (task + notification), `NOTIFICATION_SERVICE_URL`
  no task-service, `TASK_SERVICE_URL` no schedule-service (mesmo padrão do auth).
- **nginx:** conferir que `/internal` não casa com nenhum `location` (não casa hoje) e
  que os serviços escutam só em localhost no VPS.
- **Testes:** unit + integração por fase; teste de contrato do `/tasks/report` no
  schedule (espelhando o `JwtUtilTest` de contrato criado na auditoria).
- **Docs:** atualizar `README.md` e `docs/security.md` (endpoint interno e sua proteção).

## 5. Riscos e definições

| Ponto | Definição |
|---|---|
| Timezone | Semana começa segunda (como o front calcula); datas `LocalDate` do usuário |
| Sinal do desvio | `real − planejado` (positivo = executou mais que o planejado) |
| Notificação perdida (serviço fora) | Aceito na v1 (best-effort + log); evolução: padrão outbox |
| `/internal` cria notificação p/ userId arbitrário | Mitigado: segredo por env + rota não exposta; revisar se surgir múltiplos hosts |
| Ordem de entrega | Fase 1 → 2 → 3 (cada uma vira um PR); Fase 4 por último, quando os contratos estiverem no ar |
