# Plano de Refactor Arquitetural — JustDoIt (backend)

> Data: 2026-07-17

## Contexto

Os serviços estão desorganizados: a feature `task` do task-service virou um pacote gigante (34 arquivos: Task, SubTask, Timer, TaskNote, FocusSession, CycleConfig, relatórios, jobs e até o client HTTP de notificação); a feature `account` tem nome enganoso (parece perfil de usuário, mas só apaga dados locais na exclusão de conta); e `JwtUtil`/`JwtAuthFilter`/`WebSecurityConfig`/`GlobalExceptionHandler` estão copiados nos 4 serviços. Além disso, o frontend vai ganhar uma aba "Anotações" com várias notas por usuário — e o `UserNote` atual (tabela `user_note`, `user_id` UNIQUE, um bloco único por usuário no topo do To Do) não comporta isso.

**Decisões tomadas:**
1. Criar entidade nova `Note` (várias por usuário); a nota central do To Do vira uma nota **fixada** dentro de `note`; `GET/PUT /me/note` continua funcionando (compat com o frontend atual) operando sobre a nota fixada; dados de `user_note` migrados; feature `usernote` aposentada.
2. `Note` mora no task-service.
3. Escopo completo: reorganizar features **e** extrair módulo Gradle compartilhado (`libs/common`).

Convenções do projeto: branch `feature/JD-XX-nome`, commit `[JD-XX] Descrição`, tudo via PR, testes `*ServiceTest` (Mockito) + `*ControllerTest` (MockMvc). Frontend é repo separado (`justdoit-frontend`) — fora de escopo.

## Fases (cada fase = 1 PR a partir de main)

| Fase | Branch | Conteúdo |
|---|---|---|
| 0 | `chore/JD-XX-limpeza-repo` | Housekeeping: out/, .idea, trabalho staged pendente |
| A1 | `feature/JD-XX-modulo-common` | Cria `libs/common` + migra task-service |
| A2 | `feature/JD-XX-auth-usa-common` | auth-service usa o common |
| A3 | `feature/JD-XX-schedule-notification-usam-common` | schedule + notification usam o common |
| B | `feature/JD-XX-reorganiza-task-service` | Subfeatures do task-service + rename account→userdata |
| C | `feature/JD-XX-feature-note` | Feature Note + migração de user_note + nginx |
| E | `docs/JD-XX-atualiza-readme` | README e docs |

Ordem deliberada: common antes da reorganização (controllers são reescritos uma vez só, já no padrão novo); Note por último (nasce no layout novo). A2 e A3 podem andar em paralelo após A1.

---

## Fase 0 — Housekeeping

Há ~212 arquivos staged em main misturando trabalho legítimo (docs, testes, application-test.yml) com lixo: `services/auth-service/out/**` (.class do IntelliJ) e `.idea/*`.

1. `git restore --staged services/auth-service/out .idea` (out/ está com status A — sair do índice basta).
2. `git rm -r --cached .idea`.
3. Adicionar `out/` ao `.gitignore`.
4. Commitar o trabalho legítimo pendente em branch própria e mergear ANTES do refactor (senão todo PR arrasta esse diff).
5. **Não reverter** a deleção staged de `docs/secrets.md` (auditoria JWT mandou expurgar; o filter-repo do histórico é card à parte).

Verificação: `git status` limpo; `./gradlew build` verde.

---

## Fase A — Módulo `libs/common`

**Decisões:**
- Local: `libs/common` (biblioteca, não serviço). `settings.gradle.kts` ganha `include("libs:common")`.
- **Geração de token fica LOCAL no auth-service.** O common só tem validação (`JwtValidator`) + constantes `ISSUER`/`AUDIENCE` (fonte única — hoje hardcoded em 4 lugares). Auth referencia as constantes ao gerar.
- **`WebSecurityConfig` fica por serviço** (o do auth difere de verdade: permitAll em /auth/**, RateLimitFilter; compartilhar os outros 3 exigiria autoconfig condicional que não paga o custo).
- **Fim do padrão `extractUserId(header)` repetido:** o `JwtAuthFilter` já seta o UUID do usuário como principal no SecurityContext — todo controller passa a usar `@AuthenticationPrincipal UUID userId` no lugar de `HttpServletRequest` + método privado. Zero código novo pra isso.
- Wiring: `@SpringBootApplication(scanBasePackages = {"com.justdoit.<svc>", "com.justdoit.common"})` em cada Application.

### A1 — Criar módulo + migrar task-service

Criar:
- `libs/common/build.gradle.kts` — `java-library` + `io.spring.dependency-management` (BOM Spring Boot 3.4.1), deps: starter-web, starter-security, jjwt 0.12.5, lombok; plugin `java-test-fixtures`.
- `libs/common/src/main/java/com/justdoit/common/security/JwtValidator.java` — validação do JwtUtil atual do task-service, renomeado (não colide com o JwtUtil gerador do auth): `validateToken`, `extractUserId`, `extractEmail`, constantes públicas `ISSUER`/`AUDIENCE`, `@Value("${jwt.secret}")`.
- `libs/common/src/main/java/com/justdoit/common/security/JwtAuthFilter.java` — cópia fiel (preservar o override `shouldNotFilterErrorDispatch() → false` e seu comentário).
- `libs/common/src/main/java/com/justdoit/common/web/GlobalExceptionHandler.java` + `ErrorResponse.java` (record vindo do auth).
- Test fixture `libs/common/src/testFixtures/java/com/justdoit/common/security/AuthTestSupport.java`: `authenticatedUser(UUID)` → `RequestPostProcessor` com `UsernamePasswordAuthenticationToken(userId, null, List.of())`.
- Mover `JwtUtilTest` do task-service para `JwtValidatorTest` no common.

Modificar task-service: `build.gradle.kts` (+`implementation(project(":libs:common"))`, `testImplementation(testFixtures(...))`); `TaskServiceApplication` (scanBasePackages); `WebSecurityConfig` (import do filtro do common); **todos os controllers** trocam JwtUtil/extractUserId por `@AuthenticationPrincipal UUID userId`.

Deletar do task-service: `config/JwtUtil.java`, `config/JwtAuthFilter.java`, `shared/GlobalExceptionHandler.java`.

**Impacto em testes (maior custo da fase):** nos `*ControllerTest`, remover `@MockitoBean JwtUtil` e trocar `@WithMockUser` (principal String, não serve mais) por `.with(AuthTestSupport.authenticatedUser(USER_ID))`. Mecânico, mas toca todos os ControllerTests do task-service.

Verificação: `./gradlew :libs:common:test :services:task-service:test`; subir auth+task local, login real + `GET /tasks`; token inválido → 403; body inválido → 400 (valida o shouldNotFilterErrorDispatch).

### A2 — auth-service

`JwtUtil` do auth mantém geração, usa `JwtValidator.ISSUER/AUDIENCE`, perde a validação duplicada. Deletar `JwtAuthFilter`, `GlobalExceptionHandler`, `ErrorResponse` locais (RateLimitFilter importa ErrorResponse do common). `@AuthenticationPrincipal` nos controllers. Verificação-chave: `AuthIntegrationTest` (fluxo real de token).

### A3 — schedule + notification

Mesma receita, menor. **Atenção:** o `WebSecurityConfig` do notification trata `/internal/notifications` via `X-Internal-Token` — permanece local e intocado.

---

## Fase B — Reorganização do task-service

Só movimentação de pacotes — nenhuma rota, tabela ou anotação de persistência muda (rotas vivem em `@RequestMapping`, tabelas em `@Table`). Compilador garante.

| Novo pacote | Conteúdo |
|---|---|
| `feature/task` | Task, TaskController/Service/Repository, SubTask(+Repo), TaskCompletedEvent, OverdueTaskJob + DTOs de task/subtask |
| `feature/tasknote` | TaskNote C/S/R + DTOs (tabela e rota próprias `/tasks/{id}/note`; simétrico à futura `feature/note`) |
| `feature/timer` | TaskTimer C/S/R + DTOs |
| `feature/focussession` | FocusSession C/S/R + DTOs |
| `feature/cycle` | CycleConfig C/S/R, CycleMaterializer, CycleInstanceJob + DTOs |
| `feature/moduleconfig` | TaskModuleConfig C/S/R + DTOs |
| `feature/report` | TaskReportController/Service + DTO |
| `integration/` (fora de feature/) | NotificationClient, TaskCompletedListener — integração entre serviços, não domínio |
| `feature/userdata` (rename de `feature/account`) | UserDataController, UserDataService (rota `/me/data` não muda — o TaskServiceClient do auth não é afetado) |
| `shared/` | Só os enums (Priority, TaskStatus, SessionType, IntervalUnit, CycleType) |

DTOs saem do `shared/` para junto da sua feature. Testes movem para pacotes espelhados (ajuste só de package/import).

Verificação: `./gradlew :services:task-service:test` (162 testes verdes) + smoke nas rotas principais.

---

## Fase C — Feature Note

**Migração de dados: `ApplicationRunner` idempotente, SEM Flyway.** Motivo: os 4 serviços compartilham `justdoit_db` com `ddl-auto:update`; adotar Flyway exige baseline — é card futuro, não carona. O runner é seguro porque `ddl-auto:update` cria a tabela `note` nova e **nunca dropa/renomeia** `user_note` (fica como backup). Reutilizar o **mesmo `id`** na cópia → `GET /me/note` devolve o mesmo id de antes.

Criar em `services/task-service/.../feature/note/`:
- `Note.java` — `@Table(name="note", indexes=@Index(columnList="user_id"))`: `UUID id`, `UUID userId` (not null), `String title` (nullable, 255), `String content` (TEXT), `boolean pinned`, `@CreationTimestamp createdAt`, `@UpdateTimestamp updatedAt`.
- `NoteRepository.java` — `findByUserIdOrderByPinnedDescUpdatedAtDesc`, `findByUserIdAndPinnedTrue`, `findByIdAndUserId`, `deleteByUserId`.
- `NoteService.java` — `list/create/get/update/delete`, `pin(userId, noteId)` (transacional: despina a anterior), `getPinned` (devolve vazio em vez de 404 — contrato atual de `UserNoteService.getNote`: `(null, "", null, null)`), `upsertPinned`. **Constraint "uma fixada por usuário" na camada de serviço, `@Transactional`** (MySQL não tem unique parcial criável pelo ddl-auto; documentar no Javadoc).
- `NoteController.java` — `/notes`: GET (lista), POST (201), GET/{id}, PUT/{id}, DELETE/{id} (204), PATCH/{id}/pin. Tudo com `@AuthenticationPrincipal UUID userId`.
- `PinnedNoteCompatController.java` — `GET/PUT /me/note` delegando a getPinned/upsertPinned. **JSON idêntico ao `UserNoteResponse` atual** (`id`, `content`, `createdAt`, `updatedAt`); GET sem nota → bloco vazio, não 404.
- DTOs no pacote: `NoteRequest(title, content)`, `NoteResponse(id, title, content, pinned, createdAt, updatedAt)`, `MeNoteRequest(content)`, `MeNoteResponse(id, content, createdAt, updatedAt)`.
- `UserNoteMigrationRunner.java` — ApplicationRunner + JdbcTemplate: (1) se `user_note` não existe no schema (testes/instalação nova), retorna; (2) INSERT idempotente:
  ```sql
  INSERT INTO note (id, user_id, title, content, pinned, created_at, updated_at)
  SELECT un.id, un.user_id, NULL, un.content, 1, un.created_at, un.updated_at
  FROM user_note un
  WHERE NOT EXISTS (SELECT 1 FROM note n WHERE n.user_id = un.user_id AND n.pinned = 1)
  ```
  Logar contagem. `user_note` fica como backup; `DROP TABLE` manual ~1 sprint depois (documentar no PR).

Modificar:
- `feature/userdata/UserDataService`: trocar `UserNoteRepository` por `noteRepository.deleteByUserId(userId)`.
- `infra/nginx.conf`: location do task-service vira `~ ^/(tasks|categories|notes)(/|$)` **e adicionar `location = /me/note`** → 8081 (gap pré-existente: `/me/note` não é roteado hoje — confirmado no arquivo).

Deletar: `feature/usernote/` (4 arquivos), DTOs `UserNoteRequest/Response`, `UserNoteServiceTest`.

Testes novos (`feature/note/`): `NoteServiceTest` (CRUD, ownership, pin despina anterior, upsert cria/edita, getPinned vazio); `NoteControllerTest` (status codes + shape do JSON, incluindo compat `/me/note`); `UserNoteMigrationRunnerTest` (H2: criar `user_note` via JdbcTemplate, rodar o runner 2x, verificar cópia única e pinned=1 — atenção à sintaxe de information_schema no H2).

Verificação manual: banco local com linhas em `user_note` → subir serviço, conferir log de migração; `GET /me/note` devolve mesmo conteúdo e mesmo id; `GET /notes` lista a fixada; fixar outra nota e conferir `/me/note` refletindo.

---

## Fase D — schedule-service: NÃO reorganizar (decisão)

`feature/schedule` tem 8 arquivos/400 linhas com um único Controller/Service coeso. Fatiar seria churn sem ganho. Registrar a decisão no README (fase E).

## Fase E — Docs

README: corrigir seção de auth (remover `JwtToken`/tabela `jwt_token` inexistentes; real = access token stateless 15min + `RefreshToken` persistido com CleanupJob); documentar `libs/common` (regra: geração de token só no auth), novo mapa de subfeatures do task-service, rota `/notes` e `/me/note`, migração `user_note`→`note`, decisão do schedule-service. Revisar `docs/TESTES.md` e `docs/INFRA-DEPLOY.md`.

---

## Riscos e salvaguardas

1. **`ddl-auto:update` nunca renomeia/dropa** → por isso tabela nova + cópia via runner; fase B não toca nenhuma anotação de persistência.
2. **Frontend intocado:** nenhum `@RequestMapping` existente muda; `/me/note` mantém verbo, shape e semântica; fase C só adiciona `/notes`.
3. **`X-Internal-Token` e `TaskServiceClient` (auth→task `/me/data`)** não mudam de contrato em nenhuma fase.
4. Cada PR mantém `./gradlew build` verde; fase A é a única que mexe em wiring (AuthIntegrationTest é a rede de segurança); B é movimento puro; C tem testes dedicados.
