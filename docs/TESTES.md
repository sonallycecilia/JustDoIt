# Testes do Sistema JustDoIt

Visão geral de todos os testes automatizados do sistema (4 microsserviços).

**Total: 159 testes em 20 arquivos.**

| Serviço | Arquivos | Testes |
|---------|----------|--------|
| auth-service | 2 | 25 |
| task-service | 14 | 99 |
| schedule-service | 2 | 18 |
| notification-service | 2 | 17 |

Padrão geral: cada feature tem um `*ServiceTest` (lógica de negócio, unitário com **Mockito**) e um `*ControllerTest` (camada web com **MockMvc**). O auth-service tem ainda um teste de integração ponta a ponta.

---

## auth-service (25)

### AuthServiceTest (11) — unitário
- `configurarExpiracaoDoRefreshToken` — configura expiração do refresh token
- `register_deveRetornarToken_quandoDadosValidos` — registro válido retorna token
- `register_deveLancarExcecao_quandoEmailJaExiste` — e-mail duplicado lança exceção
- `register_deveSalvarSenhaComoHash` — senha é persistida como hash (não em texto puro)
- `login_deveRetornarToken_quandoCredenciaisValidas` — login válido retorna token
- `login_deveLancarExcecao_quandoEmailNaoExiste` — e-mail inexistente lança exceção
- `login_deveLancarExcecao_quandoSenhaErrada` — senha errada lança exceção
- `login_deveDarMesmoErroPraEmailESenhaErrados` — mesma mensagem de erro p/ e-mail e senha inválidos (anti-enumeração)
- `refresh_deveEmitirNovosTokens_eRotacionar` — refresh emite novos tokens e rotaciona
- `refresh_deveLancarExcecao_quandoTokenInexistente` — refresh com token inexistente lança exceção
- `refresh_deveRejeitar_quandoTokenExpirado` — refresh rejeita token expirado
- `logout_deveRevogarRefreshTokens` — logout revoga os refresh tokens

### AuthIntegrationTest (14) — integração (MockMvc + contexto Spring)
- `register_deveRetornar201EToken_quandoDadosValidos` — POST /register → 201 + token
- `register_deveRetornar400_quandoEmailDuplicado` — e-mail duplicado → 400
- `register_deveRetornar400ComErrosDeCampo_quandoDadosInvalidos` — validação de campos → 400 com detalhes
- `login_deveRetornar200EToken_quandoCredenciaisValidas` — login → 200 + token
- `login_deveRetornar401_quandoEmailNaoExiste` — e-mail inexistente → 401
- `login_deveRetornar401_quandoSenhaErrada` — senha errada → 401
- `login_deveDarMesmaRespostaPraEmailESenhaErrados` — resposta idêntica (anti-enumeração)
- `me_deveRetornar200ComDadosDoUsuario_quandoAutenticado` — GET /me autenticado → 200
- `me_deveRetornar403_quandoNaoAutenticado` — /me sem auth → 403
- `logout_deveRetornar204_quandoAutenticado` — logout → 204
- `logout_deveRetornar403_quandoNaoAutenticado` — logout sem auth → 403
- `refresh_deveRetornar200ComNovoToken_quandoRefreshTokenValido` — refresh válido → 200
- `refresh_deveRetornar401_quandoRefreshTokenInvalido` — refresh inválido → 401
- `refresh_deveRetornar401_aposLogout` — refresh após logout → 401 (token revogado)

---

## task-service (99)

### feature/task — TaskServiceTest (14)
- `createTask_withoutCategory_savesTask` — cria tarefa sem categoria
- `createTask_withCategory_loadsCategory` — cria com categoria carregada
- `createTask_categoryNotFound_throwsException` — categoria inexistente → exceção
- `getTaskById_returnsResponse` — busca por id
- `getTaskById_notFound_throwsException` — não encontrada → exceção
- `getAllTasksByUser_returnsList` — lista do usuário
- `updateTask_updatesFieldsAndSaves` — atualiza campos
- `updateTask_notFound_throwsException` — atualizar inexistente → exceção
- `deleteTask_callsDelete` — exclui tarefa
- `deleteTask_notFound_throwsException` — excluir inexistente → exceção
- `completeTask_setsStatusCompleted` — marca como COMPLETED
- `addSubTask_savesAndReturnsResponse` — adiciona subtarefa
- `getSubTaskProgress_noSubTasks_returnsZero` — progresso sem subtarefas → 0.0
- `getSubTaskProgress_someCompleted_returnsRatio` — 2 de 4 → 0.5

### feature/task — TaskControllerTest (12)
- `createTask_returnsCreated` — POST → 201
- `createTask_withBlankTitle_returnsBadRequest` — título vazio → 400
- `getAllTasks_returnsOk` — GET lista → 200
- `getTaskById_returnsOk` — GET por id → 200
- `getTaskById_notFound_returns404` — id inexistente → 404
- `updateTask_returnsOk` — PUT → 200
- `updateTask_notFound_returns404` — atualizar inexistente → 404
- `deleteTask_returnsNoContent` — DELETE → 204
- `deleteTask_notFound_returns404` — excluir inexistente → 404
- `completeTask_returnsOk` — completar → 200
- `addSubTask_returnsCreated` — adicionar subtarefa → 201
- `getSubTaskProgress_returnsOk` — progresso → 200

### feature/task — FocusSessionServiceTest (8)
- `listSessions_returnsList` — lista sessões
- `listSessions_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `createSession_createsAndReturns` — cria sessão
- `createSession_whenCompletedNull_defaultsFalse` — completed nulo assume false
- `completeSession_setsCompletedTrue` — completa sessão
- `completeSession_whenSessionNotFound_throwsException` — sessão inexistente → exceção
- `deleteSession_deletesSession` — exclui sessão
- `deleteSession_whenTaskNotFound_throwsException` — tarefa inexistente → exceção

### feature/task — FocusSessionControllerTest (6)
- `listSessions_returnsOk` — GET → 200
- `listSessions_whenTaskNotFound_returns404` — tarefa inexistente → 404
- `createSession_returnsCreated` — POST → 201
- `completeSession_returnsOk` — completar → 200
- `deleteSession_returnsNoContent` — DELETE → 204
- `deleteSession_whenNotFound_returns404` — inexistente → 404

### feature/task — TaskTimerServiceTest (7)
- `getTimer_returnsResponse` — busca timer
- `getTimer_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `getTimer_whenTimerNotFound_throwsException` — timer inexistente → exceção
- `upsertTimer_whenTimerAbsent_createsNew` — cria timer
- `upsertTimer_whenTimerPresent_updatesFields` — atualiza timer
- `logSeconds_addsToActualSeconds` — soma segundos ao acumulado
- `logSeconds_whenTimerNotFound_throwsException` — timer inexistente → exceção

### feature/task — TaskTimerControllerTest (5)
- `getTimer_returnsOk` — GET → 200
- `getTimer_whenNotFound_returns404` — inexistente → 404
- `upsertTimer_returnsOk` — upsert → 200
- `logSeconds_returnsOk` — log → 200
- `logSeconds_whenTimerNotFound_returns404` — inexistente → 404

### feature/task — TaskNoteServiceTest (6)
- `getNote_returnsResponse` — busca nota
- `getNote_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `getNote_whenNoteNotFound_throwsException` — nota inexistente → exceção
- `upsertNote_whenNoteAbsent_createsNew` — cria nota
- `upsertNote_whenNotePresent_updatesContent` — atualiza conteúdo
- `upsertNote_whenTaskNotFound_throwsException` — tarefa inexistente → exceção

### feature/task — TaskNoteControllerTest (4)
- `getNote_returnsOk` — GET → 200
- `getNote_whenNotFound_returns404` — inexistente → 404
- `upsertNote_returnsOk` — upsert → 200
- `upsertNote_withBlankContent_returnsBadRequest` — conteúdo vazio → 400

### feature/task — TaskModuleConfigServiceTest (6)
- `getConfig_returnsResponse` — busca config
- `getConfig_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `getConfig_whenConfigNotFound_throwsException` — config inexistente → exceção
- `upsertConfig_whenConfigAbsent_createsNew` — cria config
- `upsertConfig_whenConfigPresent_updatesFields` — atualiza config
- `upsertConfig_whenTaskNotFound_throwsException` — tarefa inexistente → exceção

### feature/task — TaskModuleConfigControllerTest (4)
- `getConfig_returnsOk` — GET → 200
- `getConfig_whenNotFound_returns404` — inexistente → 404
- `upsertConfig_returnsOk` — upsert → 200
- `upsertConfig_whenTaskNotFound_returns404` — tarefa inexistente → 404

### feature/task — CycleConfigServiceTest (6)
- `getCycleConfig_returnsResponse` — busca config de ciclo
- `getCycleConfig_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `getCycleConfig_whenConfigNotFound_throwsException` — config inexistente → exceção
- `upsertCycleConfig_whenAbsent_createsNew` — cria config
- `upsertCycleConfig_whenPresent_updatesCycleType` — atualiza tipo de ciclo
- `upsertCycleConfig_whenTaskNotFound_throwsException` — tarefa inexistente → exceção

### feature/task — CycleConfigControllerTest (4)
- `getCycleConfig_returnsOk` — GET → 200
- `getCycleConfig_whenNotFound_returns404` — inexistente → 404
- `upsertCycleConfig_returnsOk` — upsert → 200
- `upsertCycleConfig_withNullCycleType_returnsBadRequest` — tipo nulo → 400

### feature/category — CategoryServiceTest (8)
- `getAllByUser_returnsList` — lista categorias do usuário
- `getById_returnsResponse` — busca por id
- `getById_notFound_throwsException` — inexistente → exceção
- `create_savesAndReturnsResponse` — cria categoria
- `update_updatesFieldsAndSaves` — atualiza categoria
- `update_notFound_throwsException` — atualizar inexistente → exceção
- `delete_reassignsTasksToGenericAndDeletes` — ao excluir, reatribui tarefas à categoria genérica
- `delete_notFound_throwsException` — excluir inexistente → exceção

### feature/category — CategoryControllerTest (9)
- `getAll_returnsOk` — GET lista → 200
- `getById_returnsOk` — GET por id → 200
- `getById_notFound_returns404` — inexistente → 404
- `create_returnsCreated` — POST → 201
- `create_withBlankName_returnsBadRequest` — nome vazio → 400
- `update_returnsOk` — PUT → 200
- `update_notFound_returns404` — atualizar inexistente → 404
- `delete_returnsNoContent` — DELETE → 204
- `delete_notFound_returns404` — excluir inexistente → 404

---

## schedule-service (18)

### ScheduleServiceTest (9)
- `createTimeBlock_savesAndReturnsResponse` — cria bloco de tempo
- `getTimeBlocksByDate_returnsList` — lista blocos por data
- `createWeeklyPlan_savesAndReturnsResponse` — cria plano semanal
- `closeWeeklyPlan_setsStatusClosed` — fecha plano semanal
- `closeWeeklyPlan_notFound_throwsException` — plano inexistente → exceção
- `generateWeeklySummary_calculatesTotals` — gera resumo semanal com totais
- `generateWeeklySummary_planNotFound_throwsException` — plano inexistente → exceção
- `overlaps_whenBlocksOverlap_returnsTrue` — detecta sobreposição de blocos
- `overlaps_whenBlocksDoNotOverlap_returnsFalse` — sem sobreposição → false

### ScheduleControllerTest (9)
- `createTimeBlock_returnsCreated` — POST bloco → 201
- `createTimeBlock_missingStartDateTime_returnsBadRequest` — sem data/hora início → 400
- `getTimeBlocksByDate_returnsOk` — GET por data → 200
- `createWeeklyPlan_returnsCreated` — POST plano → 201
- `closeWeeklyPlan_returnsOk` — fechar plano → 200
- `closeWeeklyPlan_notFound_returns404` — inexistente → 404
- `generateWeeklySummary_returnsOk` — gerar resumo → 200
- `generateWeeklySummary_notFound_returns404` — inexistente → 404
- `getWeeklySummary_returnsOk` — GET resumo → 200

---

## notification-service (17)

### NotificationServiceTest (9)
- `createNotification_savesAndReturnsResponse` — cria notificação
- `markAsRead_setsReadTrue` — marca como lida
- `markAsRead_notFound_throwsException` — inexistente → exceção
- `markAsRead_wrongUser_throwsException` — usuário errado → exceção (autorização)
- `getUnreadByUser_returnsList` — lista não lidas
- `getAllByUser_returnsList` — lista todas
- `getOrCreatePreference_whenExists_returnsExisting` — retorna preferência existente
- `getOrCreatePreference_whenAbsent_createsNew` — cria preferência ausente
- `updatePreference_updatesOnlyProvidedFields` — atualiza só os campos informados

### NotificationControllerTest (8)
- `createNotification_returnsCreated` — POST → 201
- `createNotification_missingTitle_returnsBadRequest` — sem título → 400
- `getAll_returnsOk` — GET todas → 200
- `getUnread_returnsOk` — GET não lidas → 200
- `markAsRead_returnsOk` — marcar lida → 200
- `markAsRead_notFound_returns404` — inexistente → 404
- `getPreferences_returnsOk` — GET preferências → 200
- `updatePreferences_returnsOk` — PUT preferências → 200
