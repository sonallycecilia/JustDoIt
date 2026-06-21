# Documentação de Testes — JustDoIt

> Total de testes: **73**
> Última atualização: 2026-06-16

---

## Sumário

- [auth-service](#auth-service) — 18 testes
  - [AuthServiceTest](#authservicetest-unit--mockito) — 7 testes
  - [AuthIntegrationTest](#authintegrationtest-integração--springboottest) — 11 testes
- [task-service](#task-service) — 55 testes
  - [TaskModuleConfigServiceTest](#taskmoduleconfigservicetest-unit--mockito) — 5 testes
  - [TaskTimerServiceTest](#tasktimerservicetest-unit--mockito) — 7 testes
  - [TaskNoteServiceTest](#tasknoteservicetest-unit--mockito) — 6 testes
  - [FocusSessionServiceTest](#focussessionservicetest-unit--mockito) — 8 testes
  - [CycleConfigServiceTest](#cycleconfigservicetest-unit--mockito) — 6 testes
  - [TaskModuleConfigControllerTest](#taskmoduleconfigcontrollertest-controller--webmvctest) — 4 testes
  - [TaskTimerControllerTest](#tasktimercontrollertest-controller--webmvctest) — 5 testes
  - [TaskNoteControllerTest](#tasknotecontrollertest-controller--webmvctest) — 4 testes
  - [FocusSessionControllerTest](#focussessioncontrollertest-controller--webmvctest) — 6 testes
  - [CycleConfigControllerTest](#cycleconfigcontrollertest-controller--webmvctest) — 4 testes

---

## auth-service

### AuthServiceTest — Unit / Mockito

**Arquivo:** `auth-service/src/test/java/com/justdoit/auth/feature/auth/AuthServiceTest.java`  
**Tipo:** Teste unitário — `@ExtendWith(MockitoExtension.class)`  
**Mocks:** `UserRepository`, `JwtTokenRepository`, `PasswordEncoder`, `JwtUtil`

| # | Método de teste | Cenário |
|---|---|---|
| 1 | `register_deveRetornarToken_quandoDadosValidos` | Registro com dados válidos retorna token |
| 2 | `register_deveLancarExcecao_quandoEmailJaExiste` | Registro com email duplicado lança `IllegalArgumentException` e não salva no banco |
| 3 | `register_deveSalvarSenhaComoHash` | Senha é salva como hash bcrypt, nunca em texto puro |
| 4 | `login_deveRetornarToken_quandoCredenciaisValidas` | Login com credenciais corretas retorna token |
| 5 | `login_deveLancarExcecao_quandoEmailNaoExiste` | Login com email inexistente lança `IllegalArgumentException` |
| 6 | `login_deveLancarExcecao_quandoSenhaErrada` | Login com senha errada lança `IllegalArgumentException` e não gera token |
| 7 | `login_deveDarMesmoErroPraEmailESenhaErrados` | Email inválido e senha inválida retornam a mesma mensagem de erro (segurança) |

---

### AuthIntegrationTest — Integração / @SpringBootTest

**Arquivo:** `auth-service/src/test/java/com/justdoit/auth/feature/auth/AuthIntegrationTest.java`  
**Tipo:** Teste de integração — `@SpringBootTest @AutoConfigureMockMvc @Transactional`  
**Observação:** Requer MySQL e Redis em execução. Cada teste é revertido via `@Transactional`.

#### POST /auth/register

| # | Método de teste | Cenário | Status esperado |
|---|---|---|---|
| 1 | `register_deveRetornar201EToken_quandoDadosValidos` | Dados válidos retornam token | `201 Created` |
| 2 | `register_deveRetornar400_quandoEmailDuplicado` | Email já cadastrado retorna erro | `400 Bad Request` + `error: "Email already registered"` |
| 3 | `register_deveRetornar400ComErrosDeCampo_quandoDadosInvalidos` | Campos inválidos (nome vazio, email malformado, senha curta, birthDate nulo) | `400 Bad Request` com erros por campo |

#### POST /auth/login

| # | Método de teste | Cenário | Status esperado |
|---|---|---|---|
| 4 | `login_deveRetornar200EToken_quandoCredenciaisValidas` | Credenciais corretas retornam token | `200 OK` |
| 5 | `login_deveRetornar401_quandoEmailNaoExiste` | Email não cadastrado | `401 Unauthorized` + `error: "Invalid credentials"` |
| 6 | `login_deveRetornar401_quandoSenhaErrada` | Senha incorreta | `401 Unauthorized` + `error: "Invalid credentials"` |
| 7 | `login_deveDarMesmaRespostaPraEmailESenhaErrados` | Email inválido e senha inválida geram a mesma resposta (não vaza qual campo falhou) | `401 Unauthorized` com mensagens idênticas |

#### GET /auth/me

| # | Método de teste | Cenário | Status esperado |
|---|---|---|---|
| 8 | `me_deveRetornar200ComDadosDoUsuario_quandoAutenticado` | Token válido retorna dados do usuário logado | `200 OK` com `id`, `name`, `email`, `birthDate` |
| 9 | `me_deveRetornar403_quandoNaoAutenticado` | Sem token | `403 Forbidden` |

#### POST /auth/logout

| # | Método de teste | Cenário | Status esperado |
|---|---|---|---|
| 10 | `logout_deveRetornar204_quandoAutenticado` | Token válido invalida a sessão | `204 No Content` |
| 11 | `logout_deveRetornar403_quandoNaoAutenticado` | Sem token | `403 Forbidden` |

---

## task-service

> Todos os testes de controller usam `@WebMvcTest` com `@WithMockUser` e `@MockBean JwtUtil`.  
> Todos os testes de service usam `@ExtendWith(MockitoExtension.class)` sem contexto Spring.

---

### TaskModuleConfigServiceTest — Unit / Mockito

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/TaskModuleConfigServiceTest.java`  
**Mocks:** `TaskRepository`, `TaskModuleConfigRepository`

| # | Método de teste | Cenário |
|---|---|---|
| 1 | `getConfig_returnsResponse` | Busca configuração existente e retorna todos os campos corretamente |
| 2 | `getConfig_whenTaskNotFound_throwsException` | Task não encontrada lança `IllegalArgumentException` |
| 3 | `getConfig_whenConfigNotFound_throwsException` | Task existe mas config não existe lança `IllegalArgumentException` |
| 4 | `upsertConfig_whenConfigAbsent_createsNew` | Config inexistente cria novo registro via `save` |
| 5 | `upsertConfig_whenConfigPresent_updatesFields` | Config existente atualiza apenas os campos não nulos do request |
| 6 | `upsertConfig_whenTaskNotFound_throwsException` | Task não encontrada lança exceção sem chamar `save` |

---

### TaskTimerServiceTest — Unit / Mockito

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/TaskTimerServiceTest.java`  
**Mocks:** `TaskRepository`, `TaskTimerRepository`

| # | Método de teste | Cenário |
|---|---|---|
| 1 | `getTimer_returnsResponse` | Busca timer e retorna `estimatedMinutes` e `actualSeconds` corretamente |
| 2 | `getTimer_whenTaskNotFound_throwsException` | Task não encontrada lança `IllegalArgumentException` |
| 3 | `getTimer_whenTimerNotFound_throwsException` | Task existe mas timer não existe lança `IllegalArgumentException` |
| 4 | `upsertTimer_whenTimerAbsent_createsNew` | Timer inexistente cria novo registro |
| 5 | `upsertTimer_whenTimerPresent_updatesFields` | Timer existente atualiza `estimatedMinutes`, `actualSeconds` e `completedAt` |
| 6 | `logSeconds_addsToActualSeconds` | Soma os segundos informados ao `actualSeconds` já acumulado |
| 7 | `logSeconds_whenTimerNotFound_throwsException` | Timer inexistente ao fazer log lança `IllegalArgumentException` |

---

### TaskNoteServiceTest — Unit / Mockito

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/TaskNoteServiceTest.java`  
**Mocks:** `TaskRepository`, `TaskNoteRepository`

| # | Método de teste | Cenário |
|---|---|---|
| 1 | `getNote_returnsResponse` | Busca nota e retorna `content`, `createdAt` e `updatedAt` |
| 2 | `getNote_whenTaskNotFound_throwsException` | Task não encontrada lança `IllegalArgumentException` |
| 3 | `getNote_whenNoteNotFound_throwsException` | Task existe mas nota não existe lança `IllegalArgumentException` |
| 4 | `upsertNote_whenNoteAbsent_createsNew` | Nota inexistente cria novo registro com o conteúdo informado |
| 5 | `upsertNote_whenNotePresent_updatesContent` | Nota existente atualiza o campo `content` |
| 6 | `upsertNote_whenTaskNotFound_throwsException` | Task não encontrada lança exceção sem chamar `save` |

---

### FocusSessionServiceTest — Unit / Mockito

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/FocusSessionServiceTest.java`  
**Mocks:** `TaskRepository`, `FocusSessionRepository`

| # | Método de teste | Cenário |
|---|---|---|
| 1 | `listSessions_returnsList` | Retorna lista com todas as sessões da task, incluindo `completed` correto |
| 2 | `listSessions_whenTaskNotFound_throwsException` | Task não encontrada lança `IllegalArgumentException` |
| 3 | `createSession_createsAndReturns` | Cria sessão com os campos do request e retorna resposta |
| 4 | `createSession_whenCompletedNull_defaultsFalse` | Campo `completed` nulo no request salva como `false` |
| 5 | `completeSession_setsCompletedTrue` | Marca sessão como `completed = true` e persiste |
| 6 | `completeSession_whenSessionNotFound_throwsException` | Sessão não encontrada lança `IllegalArgumentException` |
| 7 | `deleteSession_deletesSession` | Chama `delete` no repositório com a entidade correta |
| 8 | `deleteSession_whenTaskNotFound_throwsException` | Task não encontrada lança `IllegalArgumentException` antes de buscar sessão |

---

### CycleConfigServiceTest — Unit / Mockito

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/CycleConfigServiceTest.java`  
**Mocks:** `TaskRepository`, `CycleConfigRepository`

| # | Método de teste | Cenário |
|---|---|---|
| 1 | `getCycleConfig_returnsResponse` | Busca config e retorna `cycleType`, `startDate` e `endDate` corretamente |
| 2 | `getCycleConfig_whenTaskNotFound_throwsException` | Task não encontrada lança `IllegalArgumentException` |
| 3 | `getCycleConfig_whenConfigNotFound_throwsException` | Task existe mas config não existe lança `IllegalArgumentException` |
| 4 | `upsertCycleConfig_whenAbsent_createsNew` | Config inexistente cria novo registro com `cycleType` e `startDate` |
| 5 | `upsertCycleConfig_whenPresent_updatesCycleType` | Config existente atualiza `cycleType` via `ArgumentCaptor` |
| 6 | `upsertCycleConfig_whenTaskNotFound_throwsException` | Task não encontrada lança exceção sem chamar `save` |

---

### TaskModuleConfigControllerTest — Controller / @WebMvcTest

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/TaskModuleConfigControllerTest.java`  
**Mocks:** `TaskModuleConfigService`, `JwtUtil`

| # | Método de teste | Endpoint | Status esperado |
|---|---|---|---|
| 1 | `getConfig_returnsOk` | `GET /tasks/{taskId}/module-config` | `200 OK` com campos do response |
| 2 | `getConfig_whenNotFound_returns404` | `GET /tasks/{taskId}/module-config` | `404 Not Found` |
| 3 | `upsertConfig_returnsOk` | `PUT /tasks/{taskId}/module-config` | `200 OK` com campos atualizados |
| 4 | `upsertConfig_whenTaskNotFound_returns404` | `PUT /tasks/{taskId}/module-config` | `404 Not Found` |

---

### TaskTimerControllerTest — Controller / @WebMvcTest

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/TaskTimerControllerTest.java`  
**Mocks:** `TaskTimerService`, `JwtUtil`

| # | Método de teste | Endpoint | Status esperado |
|---|---|---|---|
| 1 | `getTimer_returnsOk` | `GET /tasks/{taskId}/timer` | `200 OK` com `estimatedMinutes` e `actualSeconds` |
| 2 | `getTimer_whenNotFound_returns404` | `GET /tasks/{taskId}/timer` | `404 Not Found` |
| 3 | `upsertTimer_returnsOk` | `PUT /tasks/{taskId}/timer` | `200 OK` com `estimatedMinutes` atualizado |
| 4 | `logSeconds_returnsOk` | `PATCH /tasks/{taskId}/timer/log` | `200 OK` com `actualSeconds` acumulado |
| 5 | `logSeconds_whenTimerNotFound_returns404` | `PATCH /tasks/{taskId}/timer/log` | `404 Not Found` |

---

### TaskNoteControllerTest — Controller / @WebMvcTest

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/TaskNoteControllerTest.java`  
**Mocks:** `TaskNoteService`, `JwtUtil`

| # | Método de teste | Endpoint | Status esperado |
|---|---|---|---|
| 1 | `getNote_returnsOk` | `GET /tasks/{taskId}/note` | `200 OK` com `content` |
| 2 | `getNote_whenNotFound_returns404` | `GET /tasks/{taskId}/note` | `404 Not Found` |
| 3 | `upsertNote_returnsOk` | `PUT /tasks/{taskId}/note` | `200 OK` com `content` atualizado |
| 4 | `upsertNote_withBlankContent_returnsBadRequest` | `PUT /tasks/{taskId}/note` | `400 Bad Request` (validação `@NotBlank`) |

---

### FocusSessionControllerTest — Controller / @WebMvcTest

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/FocusSessionControllerTest.java`  
**Mocks:** `FocusSessionService`, `JwtUtil`

| # | Método de teste | Endpoint | Status esperado |
|---|---|---|---|
| 1 | `listSessions_returnsOk` | `GET /tasks/{taskId}/focus-sessions` | `200 OK` com array de 2 sessões |
| 2 | `listSessions_whenTaskNotFound_returns404` | `GET /tasks/{taskId}/focus-sessions` | `404 Not Found` |
| 3 | `createSession_returnsCreated` | `POST /tasks/{taskId}/focus-sessions` | `201 Created` com `id` e `completed: false` |
| 4 | `completeSession_returnsOk` | `PATCH /tasks/{taskId}/focus-sessions/{sessionId}/complete` | `200 OK` com `completed: true` |
| 5 | `deleteSession_returnsNoContent` | `DELETE /tasks/{taskId}/focus-sessions/{sessionId}` | `204 No Content` |
| 6 | `deleteSession_whenNotFound_returns404` | `DELETE /tasks/{taskId}/focus-sessions/{sessionId}` | `404 Not Found` |

---

### CycleConfigControllerTest — Controller / @WebMvcTest

**Arquivo:** `task-service/src/test/java/com/justdoit/task/feature/task/CycleConfigControllerTest.java`  
**Mocks:** `CycleConfigService`, `JwtUtil`

| # | Método de teste | Endpoint | Status esperado |
|---|---|---|---|
| 1 | `getCycleConfig_returnsOk` | `GET /tasks/{taskId}/cycle-config` | `200 OK` com `cycleType` e `startDate` |
| 2 | `getCycleConfig_whenNotFound_returns404` | `GET /tasks/{taskId}/cycle-config` | `404 Not Found` |
| 3 | `upsertCycleConfig_returnsOk` | `PUT /tasks/{taskId}/cycle-config` | `200 OK` com `cycleType` e `startDate` atualizados |
| 4 | `upsertCycleConfig_withNullCycleType_returnsBadRequest` | `PUT /tasks/{taskId}/cycle-config` | `400 Bad Request` (validação `@NotNull` em `cycleType`) |

---

## Legenda de tipos

| Tipo | Anotação principal | Carrega Spring? | Requer infraestrutura? |
|---|---|---|---|
| Unit / Mockito | `@ExtendWith(MockitoExtension.class)` | Não | Não |
| Controller / WebMvcTest | `@WebMvcTest` + `@WithMockUser` | Parcial (só camada web) | Não |
| Integração | `@SpringBootTest` + `@Transactional` | Sim (completo) | Sim (MySQL + Redis) |
