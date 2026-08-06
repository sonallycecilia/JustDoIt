# Testes do Sistema JustDoIt

Visão geral de todos os testes automatizados do sistema (4 microsserviços).

**Total: 305 testes em 42 arquivos** (medição de 28/07/2026, `./gradlew test`).

| Serviço | Arquivos | Testes |
|---------|----------|--------|
| task-service | 30 | 199 |
| auth-service | 5 | 40 |
| notification-service | 3 | 19 |
| schedule-service | 2 | 19 |
| libs/common | 2 | 28 |

> O catálogo por classe abaixo cobre as features nomeadas e está defasado em relação ao
> total acima; os números da tabela vêm dos relatórios em
> `<módulo>/build/test-results/test/*.xml`. As classes de métrica de qualidade estão
> detalhadas em `METRICAS-QUALIDADE.md`.

Padrão geral: cada feature tem um `*ServiceTest` (lógica de negócio, unitário com **Mockito**) e um `*ControllerTest` (camada web com **MockMvc**). O auth-service tem ainda um teste de integração ponta a ponta.

---

## auth-service (28)

### AuthServiceTest (11) — unitário
- `configurarExpiracaoDoRefreshToken` — configura expiração do refresh token
- `register_deveRetornarToken_quandoDadosValidos` — verifica se o registro é válido e retorna token
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

### AuthAccessControlMetricsTest (3) — integração / métrica de segurança
- `taxaDeBloqueioDeAcessoNaoAutorizado_deveSer1` — dispara a bateria de requisições inválidas contra todas as rotas protegidas e exige X = A/B = 1.0
- `tokenDeUmUsuario_naoAcessaDadosDeOutroUsuario` — token de um usuário só devolve os próprios dados (sem vazamento cruzado)
- `tokenForjadoComIdentidadeDeOutroUsuario_eBloqueado` — token forjado com o id de outro usuário (assinatura inválida) → 403

> **Métrica de Qualidade — Segurança: Taxa de Bloqueio de Acesso Não Autorizado**
>
> `X = A / B` &nbsp;&nbsp; (`0 ≤ X ≤ 1`; ideal = **1**)
> - **A** = requisições com token JWT ausente, inválido ou de outro usuário que foram **corretamente rejeitadas** (status 401/403).
> - **B** = total de requisições inválidas disparadas.
>
> O teste `taxaDeBloqueioDeAcessoNaoAutorizado_deveSer1` cobre as 4 rotas protegidas
> (`GET /auth/me`, `PUT /auth/me`, `DELETE /auth/me`, `POST /auth/logout`) combinadas com
> 9 cenários de autorização inválida — token **ausente**, header **vazio**, `Bearer` **sem token**,
> JWT **malformado**, **assinatura inválida** (segredo errado), token **expirado**, **esquema errado**
> (`Basic`), token **sem o prefixo** `Bearer` e token **adulterado** — totalizando **B = 36** requisições.
> A asserção exige `A == B` ⇒ **X = 1.0**; qualquer rota que não bloqueie aparece na lista de falhas.
>
> Roda em **H2 em memória** (modo MySQL), configurado em `src/test/resources/application-test.yml` —
> não exige MySQL local.

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

### feature/timer — TaskTimerServiceTest (17)
- `getTimer_returnsResponse` — busca timer
- `getTimer_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `getTimer_whenTimerNotFound_throwsException` — timer inexistente → exceção
- `upsertTimer_whenTimerAbsent_createsNew` — cria timer
- `upsertTimer_whenTimerPresent_updatesFields` — atualiza timer
- `logSeconds_addsToActualSeconds` — soma pelo UPDATE atômico, sem reescrever o valor lido
- `logSeconds_whenTimerAbsent_createsTimerWithLoggedSeconds` — primeiro log cria o timer
- `logSeconds_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `start_whenSemCronometroAtivo_acionaCronometro` — start grava o `ActiveTimer` do usuário
- `start_whenJaExisteCronometroAtivo_throwsException` — já ativo → `CronometroJaAtivoException`
- `start_whenPerdeCorridaNoInsert_throwsException` — violação do índice único → mesma exceção
- `start_whenTaskNotFound_throwsException` — tarefa inexistente → exceção
- `stop_somaTempoDecorridoEEncerraCronometro` — soma `agora − started_at` e apaga o ativo
- `stop_whenSemCronometroAtivo_throwsException` — sem cronômetro → exceção
- `stop_whenCronometroEDeOutraTarefa_throwsException` — ativo em outra tarefa → exceção
- `getActive_returnsCronometroEmCurso` — devolve o cronômetro em curso
- `getActive_whenSemCronometroAtivo_throwsException` — nenhum ativo → exceção

### feature/timer — TaskTimerControllerTest (11)
- `getTimer_returnsOk` / `getTimer_whenNotFound_returns404` — GET → 200 / 404
- `upsertTimer_returnsOk` — upsert → 200
- `logSeconds_returnsOk` / `logSeconds_whenTimerNotFound_returns404` — log → 200 / 404
- `logSeconds_whenCorridaNaCriacaoDoTimer_retentaEDevolveOk` — colisão na criação → retentativa → 200
- `start_returnsOk` — start → 200
- `start_whenJaExisteCronometroAtivo_returns409` — **409**, o status que a métrica 3 conta
- `start_whenTaskNotFound_returns404` — tarefa inexistente → 404
- `stop_returnsOk` — stop → 200
- `stop_whenSemCronometroAtivo_returns404` — sem cronômetro → 404

### feature/timer — ActiveTimerControllerTest (2)
- `getActive_returnsOk` — `GET /timers/active` → 200
- `getActive_whenSemCronometroAtivo_returns404` — nenhum ativo → 404

### qualidade — CronometroConcorrenteMetricsTest (5) — integração / métrica de desempenho
- `taxaDeBloqueioDeCronometroConcorrente_deveSer1` — 3 cenários × 5 rodadas de acionamentos realmente simultâneos; exige X = A/B = 1.0 com B = 130
- `somenteUmaTarefaAcumulaTempo` — só a tarefa do cronômetro vencedor acumula tempo; a bloqueada fica em zero
- `usoLegitimoDoCronometroContinuaFuncionando` — guarda contra falso positivo: `start → stop → start` segue aceito
- `usuariosDiferentesCronometramEmParalelo` — a trava é por usuário, não global
- `logsConcorrentesNaoPerdemTempo` — 10 logs simultâneos de 1 s somam exatamente 10 s (fora do denominador)

> **Métrica de Qualidade — Desempenho: Taxa de Bloqueio de Cronômetro Concorrente**
>
> `X = A / B` &nbsp;&nbsp; (`0 ≤ X ≤ 1`; ideal = **1**)
> - **A** = tentativas de acionamento simultâneo **bloqueadas com sucesso** (HTTP 409), mantendo ativo apenas o primeiro cronômetro.
> - **B** = tentativas de acionamento concorrente que *deviam* ser bloqueadas — numa rodada de `N` disparos por `U` usuários, `N − U`.
>
> Detalhamento completo em `METRICAS-QUALIDADE.md`.

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

### feature/export — TaskExportServiceTest (8)
- `export_trazCamposEssenciais` — nome, conclusão, categoria, datas, estimativa, cronômetro e nota
- `export_modulosAusentes_naoQuebram` — tarefa sem categoria/timer/nota exporta com nulos
- `export_estimativaTemFallbackNaTarefa` — sem estimativa no timer, cai em `Task.estimatedMinutes`
- `toCsv_cabecalhoEQuebras` — CSV começa com BOM + cabeçalho e usa CRLF
- `toCsv_escapaCaracteresEspeciais` — vírgula, aspas e quebra de linha da nota (RFC 4180)
- `toCsv_semTarefas_soCabecalho` — conta vazia gera arquivo válido
- `fileName_comTimestamp` — `export_tarefas_2026-07-27.csv`
- `exportFormat_parse` — csv/CSV/vazio/inválido

### feature/export — TaskExportControllerTest (6)
- `export_json` — `?format=json` → envelope JSON como anexo `.json`
- `export_csv` — `?format=csv` → `text/csv` como anexo `.csv`
- `export_semFormato_ehJson` — sem `format`, o padrão é JSON
- `export_formatoInvalido_ehBadRequest` — formato desconhecido → 400, sem tocar no banco
- `export_usaSempreOUsuarioDoToken` — `userId` na query string é ignorado
- `export_nomeUsaDataDeHoje` — nome do arquivo carimbado com a data corrente

### feature/export — TaskExportIntegrationTest (7) — integração (MockMvc + H2)
- `export_json_isolaPorUsuario` — JSON traz só as tarefas do dono do token
- `export_csv_isolaPorUsuario` — idem no CSV
- `export_contaVazia_naoHerdaDadosAlheios` — conta sem tarefas não recebe nada de outra
- `export_trazCamposEssenciais` — conteúdo completo vindo do banco
- `export_csv_escapaNota` — nota com quebra de linha não vira registro novo
- `export_nomeDoArquivo` — `Content-Disposition` com nome e extensão certos
- `export_semToken_eBloqueado` — sem autenticação → 403

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
