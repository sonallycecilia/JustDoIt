# Segurança — JustDoIt

Documento que resume o trabalho de segurança feito no backend (branch `security`).
Cobre o hardening inicial e a implementação de refresh token com revogação.

> Commits de referência na branch `security`:
> - `35d72da` — hardening de segurança (segredos, IDOR, validação, headers)
> - `453a67d` — refresh token com rotação e revogação no logout

---

## 1. Hardening de segurança (`35d72da`)

### 1.1 Segredos sem default no código

Os 4 serviços tinham o **mesmo** segredo JWT e a senha do banco com valor *default*
embutido no `application.yml` (versionado). Qualquer um com acesso ao repositório podia
forjar tokens válidos.

| Antes | Depois |
|---|---|
| `jwt.secret: ${JWT_SECRET:<default fraco removido>}` | `jwt.secret: ${JWT_SECRET}` |
| `password: ${SPRING_DATASOURCE_PASSWORD:root}` | `password: ${SPRING_DATASOURCE_PASSWORD}` |

Sem o default, a aplicação **falha ao subir** se a variável de ambiente não existir
(comportamento *fail-safe* — evita rodar com credencial fraca por engano).

> ⚠️ O segredo antigo continua no histórico do git → deve ser considerado **comprometido**.
> Gerar um novo: `openssl rand -base64 48`.

### 1.2 Correção de IDOR em `POST /notifications`

O `userId` vinha do **corpo da requisição**, permitindo a qualquer usuário autenticado
criar notificações para qualquer outra pessoa.

```diff
- public ResponseEntity<...> createNotification(@RequestBody @Valid CreateNotificationRequest request) {
-     return ...createNotification(request);  // userId vinha do body
+ public ResponseEntity<...> createNotification(@RequestBody @Valid CreateNotificationRequest request,
+                                               HttpServletRequest httpRequest) {
+     UUID userId = extractUserId(httpRequest);   // userId vem do JWT
+     return ...createNotification(request, userId);
```

O campo `userId` foi **removido** do `CreateNotificationRequest`.

> Os demais endpoints (task, schedule, leitura de notificações) já estavam corretos:
> filtram por `findByIdAndUserId(...)` usando o `userId` do token.

### 1.3 Validação de entrada (`@Size`)

Adicionados limites de tamanho aos DTOs de texto livre para evitar payloads abusivos:

| DTO | Campos |
|---|---|
| `TaskRequest` | `title` (200), `description` (5000) |
| `SubTaskRequest` | `title` (200) |
| `CategoryRequest` | `name` (100), `color` (30), `description` (500) |
| `TaskNoteRequest` | `content` (5000) |
| `CreateNotificationRequest` | `title` (150), `message` (2000) |
| `RegisterRequest` | `name` (120), `email` (255), `password` (8–100) |

### 1.4 Headers de segurança no Nginx

Adicionados ao bloco HTTPS do `infra/nginx.conf`:

```nginx
add_header Strict-Transport-Security "max-age=31536000; includeSubDomains" always;
add_header Content-Security-Policy   "default-src 'none'; frame-ancestors 'none'" always;
add_header X-Content-Type-Options    "nosniff" always;
add_header X-Frame-Options           "DENY" always;
add_header Referrer-Policy           "no-referrer" always;
server_tokens off;
```

### 1.5 Docker Compose — Redis e MySQL

| Item | Antes | Depois |
|---|---|---|
| Senha MySQL | default `root` | `${SPRING_DATASOURCE_PASSWORD:?...}` (obrigatória) |
| Senha Redis | **nenhuma** | `--requirepass ${REDIS_PASSWORD:?...}` |
| Portas | `3306:3306` / `6379:6379` (expostas) | `127.0.0.1:3306` / `127.0.0.1:6379` (só local) |

`infra/.env.example` passou a documentar `REDIS_PASSWORD`.

---

## 2. Refresh token: rotação e revogação (`453a67d`)

Apenas o **auth-service** mudou. Os outros serviços só validam a assinatura do
access token — não precisaram de alteração.

### 2.1 Modelo de tokens

| | Access token | Refresh token |
|---|---|---|
| Formato | JWT assinado (HS256) | string opaca aleatória (256 bits) |
| Vida útil | **15 min** (`JWT_ACCESS_EXPIRATION_MS`) | **7 dias** (`JWT_REFRESH_EXPIRATION_MS`) |
| Onde fica | só no cliente (stateless) | hash **SHA-256** na tabela `refresh_token` |
| Revogável | não (expira sozinho) | **sim** (apagado no banco) |

O refresh token **nunca** é armazenado em claro — guarda-se apenas o hash SHA-256.

### 2.2 Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/auth/register` | Não | Cadastra e retorna par de tokens |
| POST | `/auth/login` | Não | Autentica, revoga sessões antigas e retorna par de tokens |
| POST | `/auth/refresh` | Não* | Valida o refresh token, **rotaciona** e retorna novo par |
| POST | `/auth/logout` | Sim | **Revoga** os refresh tokens do usuário |
| GET | `/auth/me` | Sim | Dados do usuário logado |

\* `/auth/refresh` é público porque o access token pode já ter expirado; a autorização
vem do próprio refresh token enviado no corpo.

### 2.3 Fluxo do refresh (com rotação)

```
POST /auth/refresh  { "refreshToken": "..." }
  → calcula SHA-256 do valor recebido
  → busca na tabela refresh_token (findByTokenHash)
  → invalida (apaga) o refresh token usado          ← rotação
  → se expirado: 401 Refresh token inválido
  → emite novo access + novo refresh
  → retorna { accessToken, refreshToken, expiresIn }
```

### 2.4 `AuthResponse` (mudança de contrato)

```diff
- { "token": "eyJ..." }
+ { "accessToken": "eyJ...", "refreshToken": "k3f...", "expiresIn": 900 }
```

### 2.5 Entidade `RefreshToken` — tabela `refresh_token`

| Campo | Tipo | Detalhe |
|---|---|---|
| id | UUID | gerado pelo Hibernate |
| token_hash | String(64) | SHA-256 do token (único) |
| user_id | UUID | dono do token |
| email | String | |
| profile | String | `"USER"` |
| expires_at | LocalDateTime | now + 7 dias |

> A entidade antiga `JwtToken` (tabela `jwt_token`) foi renomeada para `RefreshToken`.
> Com `ddl-auto: update` a nova tabela é criada automaticamente; a antiga `jwt_token`
> fica órfã e pode ser removida manualmente.

### 2.6 Tradeoff (intencional)

A revogação do **access token** não é instantânea: após o logout, o access ainda vale
até expirar (≤ 15 min). O refresh é revogado na hora. Esse é o padrão para JWT stateless —
revogação imediata do access exigiria uma *denylist* consultada a cada request
(ex.: Redis com `jti`), reintroduzindo estado em todos os serviços.

---

## 3. Impactos operacionais

### Variáveis de ambiente

| Variável | Obrigatória | Default |
|---|---|---|
| `JWT_SECRET` | **sim** | — (app não sobe sem) |
| `SPRING_DATASOURCE_PASSWORD` | **sim** | — |
| `REDIS_PASSWORD` | **sim** (compose) | — |
| `CORS_ALLOWED_ORIGINS` | não | `http://localhost:3000` |
| `JWT_ACCESS_EXPIRATION_MS` | não | `900000` (15 min) |
| `JWT_REFRESH_EXPIRATION_MS` | não | `604800000` (7 dias) |

### Frontend

O login/register agora retornam `accessToken` + `refreshToken` (não mais `token`).
O cliente deve:
1. guardar os dois tokens;
2. enviar `Authorization: Bearer <accessToken>` nas chamadas;
3. ao receber `401`, chamar `POST /auth/refresh` com `{ "refreshToken": "..." }` e repetir a chamada;
4. no logout, chamar `POST /auth/logout`.

### Banco

`ddl-auto: update` cria a tabela `refresh_token` no primeiro start. A tabela `jwt_token`
pode ser dropada manualmente.

---

## 4. Itens pendentes (não implementados nesta branch)

| Item | Severidade | Esforço |
|---|---|---|
| RBAC / `@PreAuthorize` (papéis além de `USER`) | Médio | Médio |
| `ddl-auto: validate` + migrações (Flyway/Liquibase) | Médio | Médio |
| `show-sql: false` em produção | Baixo | Baixo |
| Sanitização anti-XSS de campos de texto | Médio | Médio |
| Restringir `allowedHeaders` do CORS (hoje `*`) | Baixo | Baixo |
| Revogação **instantânea** do access token (denylist via Redis) | — | Alto |

---

## 5. Observações

- **`docs/secrets.md`** guarda segredos de produção em texto puro (JWT_SECRET, senha do
  banco, caminho da chave SSH). ⚠️ Ao contrário do que esta seção dizia antes, o arquivo
  **chegou a ser commitado** (commit `26d9ed4`). Em 2026-07-03 ele foi removido do índice
  (`git rm --cached`) e adicionado ao `.gitignore`, mas **o histórico ainda contém os
  segredos** — ver seção 6 para rotação e expurgo. Em produção, prefira um *secret
  manager* ou o `.env` do servidor.
- `docs/auth-service.md` ainda descreve o desenho antigo (token único de 24h, tabela
  `jwt_token`, resposta `{ token }`) e deve ser atualizado para refletir o novo fluxo.

---

## 6. Auditoria JWT (2026-07-03) — medidas implementadas

Referência: `docs/AUDITORIA-JWT-AUTH-SERVICE.md`.

### 6.1 Crítica — segredo de produção no git

`docs/secrets.md` (JWT_SECRET e senha do MySQL de produção) estava rastreado no git.
Feito: removido do índice + `.gitignore`. **Pendente (ação manual do operador):**

1. **Rotacionar imediatamente** o `JWT_SECRET` e a `SPRING_DATASOURCE_PASSWORD` no VPS
   (`openssl rand -base64 48`), atualizar o `.env` do servidor e reiniciar os 4 serviços
   — o mesmo segredo é compartilhado por todos. Todas as sessões ativas caem (re-login).
2. **Expurgar o histórico**: `git filter-repo --invert-paths --path docs/secrets.md`
   e force-push; qualquer clone/fork antigo continua contendo o segredo — por isso a
   rotação vem primeiro e é obrigatória independentemente do expurgo.

### 6.2 Correções implementadas no código

| Achado | Medida |
|---|---|
| Claims `iss`/`aud` ausentes; `type` não validado | Access token agora leva `iss=justdoit-auth-service`, `aud=justdoit-api` e `jti`; os filtros dos **4 serviços** exigem `iss`, `aud` e `type=access` na validação |
| Enumeração de e-mails / abuso de DNS sem throttling | `RateLimitFilter` (token bucket por IP, em memória) nos endpoints públicos `login/register/refresh/check-email` — default 20 req/min por IP (`AUTH_RATE_LIMIT_*`); resposta `429` + `Retry-After` |
| Reuso de refresh token rotacionado passava despercebido | Rotação agora marca o token como usado (`used_at`) em vez de apagar; se um token já usado reaparecer, **todas as sessões do usuário são revogadas** (detecção de reuso, padrão token-family) |
| Oráculo de timing no login | E-mail inexistente executa `bcrypt.matches` contra um hash dummy — mesmo custo com ou sem conta |
| Controller re-parseava o token do header | Endpoints autenticados usam `@AuthenticationPrincipal` (userId do `SecurityContext` populado pelo filtro) |
| `check-email` disparava DNS por chamada | Cache por domínio (TTL 10 min, teto de 10k entradas) no `MxEmailVerifier`, além do rate limit |
| Tabela `refresh_token` crescia sem limpeza | `RefreshTokenCleanupJob` (`@Scheduled`, diário às 03:00) remove tokens com `expires_at` vencido |

> Migração de banco: a coluna `refresh_token.used_at` é criada automaticamente pelo
> `ddl-auto: update` no primeiro start após o deploy.

### 6.3 Risco aceito formalmente — janela de 15 min do access token

Após logout ou exclusão de conta, o **access token** continua válido até expirar
(≤ 15 min) em todos os serviços: não há blacklist compartilhada (não usamos Redis; a
revogação vive na tabela MySQL `refresh_token`, consultada só no `/auth/refresh`).
**Decisão: risco aceito** pelo custo/benefício atual — revogação instantânea exigiria
uma denylist consultada a cada request em todos os serviços. O `jti` já emitido em cada
access token viabiliza essa blacklist no futuro sem quebra de contrato. Mitigações
vigentes: expiração curta (15 min) e revogação imediata do refresh token.

### 6.4 Pendências (médio prazo)

- Assinatura assimétrica (RS256/EdDSA + JWKS): hoje os 4 serviços compartilham o mesmo
  segredo HMAC — comprometer qualquer serviço permite **emitir** tokens, não só validar.
  A validação de `iss`/`aud` implementada já prepara o contrato para essa migração.
- Blacklist de access token (se o risco aceito em 6.3 for revisto).
- Resposta neutra no registro (confirmação por e-mail) para eliminar de vez a enumeração.
```
