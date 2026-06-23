# auth-service — Implementação e Testes

> Atualizado para o fluxo de **access token + refresh token** (branch `security`).
> Visão geral de segurança consolidada em [`security.md`](security.md).

## Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/auth/register` | Não | Cadastrar usuário e receber par de tokens |
| POST | `/auth/login` | Não | Autenticar e receber par de tokens |
| POST | `/auth/refresh` | Não* | Renovar o par de tokens a partir do refresh token |
| POST | `/auth/logout` | Sim | Revogar os refresh tokens do usuário |
| GET | `/auth/me` | Sim | Retornar dados do usuário logado |

\* `/auth/refresh` é público porque o access token pode já ter expirado; a autorização
vem do próprio refresh token enviado no corpo.

---

## Modelo de tokens

| | Access token | Refresh token |
|---|---|---|
| Formato | JWT assinado (HS256) | string opaca aleatória (256 bits) |
| Vida útil | 15 min (`JWT_ACCESS_EXPIRATION_MS`) | 7 dias (`JWT_REFRESH_EXPIRATION_MS`) |
| Armazenamento | só no cliente (stateless) | hash SHA-256 na tabela `refresh_token` |
| Revogável | não (expira sozinho) | sim (apagado no banco) |

---

## Fluxo de registro

```
POST /auth/register
  → valida campos (Bean Validation)
  → verifica email duplicado
  → salva usuário com senha em BCrypt
  → gera access token (JWT, 15 min, HS256)
  → gera refresh token opaco e persiste o hash SHA-256 (refresh_token)
  → retorna { accessToken, refreshToken, expiresIn }
```

## Fluxo de login

```
POST /auth/login
  → valida campos
  → busca usuário pelo email
  → compara senha com BCrypt
  → revoga refresh tokens anteriores (deleteByUserId)  ← só uma sessão ativa
  → gera novo par access + refresh (persiste hash)
  → retorna { accessToken, refreshToken, expiresIn }
```

## Fluxo de refresh (com rotação)

```
POST /auth/refresh  { "refreshToken": "..." }
  → calcula SHA-256 do valor recebido
  → busca na tabela refresh_token (findByTokenHash)
  → invalida (apaga) o refresh token usado            ← rotação
  → se inexistente ou expirado: 401 Invalid refresh token
  → gera novo par access + refresh
  → retorna { accessToken, refreshToken, expiresIn }
```

## Fluxo de logout

```
POST /auth/logout   (Authorization: Bearer <accessToken>)
  → extrai userId do access token
  → apaga todos os refresh tokens do usuário (deleteByUserId)  ← revogação
  → retorna 204
```

> O access token continua válido até expirar (≤ 15 min); o refresh é revogado na hora.
> Detalhes do tradeoff em [`security.md`](security.md).

---

## DTOs

### `RegisterRequest`
```json
{
  "name": "string (obrigatório, máx 120)",
  "email": "string válido (obrigatório, máx 255)",
  "password": "string (obrigatório, 8–100 chars)",
  "birthDate": "yyyy-MM-dd (obrigatório)"
}
```

### `LoginRequest`
```json
{
  "email": "string válido (obrigatório)",
  "password": "string (obrigatório)"
}
```

### `RefreshRequest`
```json
{ "refreshToken": "string (obrigatório)" }
```

### `AuthResponse` (retornado por register, login e refresh)
```json
{
  "accessToken": "eyJ...",
  "refreshToken": "k3f...",
  "expiresIn": 900
}
```
`expiresIn` é a validade do access token em segundos.

### `UserResponse` (retornado pelo `/auth/me`)
```json
{
  "id": "uuid",
  "name": "string",
  "email": "string",
  "birthDate": "yyyy-MM-dd",
  "createdAt": "datetime"
}
```

### `ErrorResponse` (retornado nos erros de negócio)
```json
{ "error": "mensagem do erro" }
```

---

## Respostas HTTP

| Situação | Status |
|---|---|
| Registro bem-sucedido | 201 Created |
| Login / refresh bem-sucedido | 200 OK |
| Logout bem-sucedido | 204 No Content |
| Campos inválidos | 400 Bad Request + erros por campo |
| Email duplicado | 400 Bad Request + `{ "error": "Email already registered" }` |
| Credenciais erradas | 401 Unauthorized + `{ "error": "Invalid credentials" }` |
| Refresh token inválido/expirado | 401 Unauthorized + `{ "error": "Invalid refresh token" }` |
| Sem token (rota protegida) | 403 Forbidden |

---

## Entidades

### `User` — tabela `users`
| Campo | Tipo | Detalhe |
|---|---|---|
| id | UUID | gerado pelo Hibernate (`@UuidGenerator`) |
| name | String | obrigatório |
| email | String | único |
| password_hash | String | BCrypt |
| birth_date | LocalDate | opcional |
| created_at | LocalDateTime | `@CreationTimestamp` |
| active | Boolean | default `true` |

### `RefreshToken` — tabela `refresh_token`
| Campo | Tipo | Detalhe |
|---|---|---|
| id | UUID | gerado pelo Hibernate |
| token_hash | String(64) | SHA-256 do refresh token (único) — nunca em claro |
| user_id | UUID | dono do token |
| email | String | |
| profile | String | fixo `"USER"` |
| expires_at | LocalDateTime | now + 7 dias |

> Substitui a entidade antiga `JwtToken` (tabela `jwt_token`), que guardava o JWT em claro.

---

## Segurança

- Rotas públicas: `/auth/register`, `/auth/login` e `/auth/refresh`
- Todas as demais rotas (incluindo `/auth/logout` e `/auth/me`) exigem access token válido
- `JwtAuthFilter` valida a assinatura do access token a cada requisição
- Access token curto (15 min); refresh token longo (7 dias), opaco e revogável
- Refresh token é guardado apenas como hash SHA-256; a rotação invalida o token a cada uso
- Logout revoga os refresh tokens do usuário (`deleteByUserId`)
- `JWT_SECRET` e `SPRING_DATASOURCE_PASSWORD` são obrigatórios via variável de ambiente (sem default)
- Sessão stateless, CSRF desabilitado, CORS restrito via `CORS_ALLOWED_ORIGINS`

---

## Testes

### Unitários — `AuthServiceTest`

Testam o `AuthService` isolado com mocks (sem banco, sem Spring context).

| Teste | Cenário |
|---|---|
| `register_deveRetornarToken_quandoDadosValidos` | Registro válido retorna par de tokens (access + refresh) |
| `register_deveLancarExcecao_quandoEmailJaExiste` | Email duplicado lança exceção e não salva no banco |
| `register_deveSalvarSenhaComoHash` | Senha é salva como hash BCrypt, nunca em texto puro |
| `login_deveRetornarToken_quandoCredenciaisValidas` | Login com credenciais corretas retorna par de tokens |
| `login_deveLancarExcecao_quandoEmailNaoExiste` | Email inexistente lança exceção |
| `login_deveLancarExcecao_quandoSenhaErrada` | Senha incorreta lança exceção e não gera token |
| `login_deveDarMesmoErroPraEmailESenhaErrados` | Mesma mensagem de erro para email e senha inválidos |
| `refresh_deveEmitirNovosTokens_eRotacionar` | Refresh válido emite novo par e invalida o token usado |
| `refresh_deveLancarExcecao_quandoTokenInexistente` | Refresh token inexistente lança exceção, não gera token |
| `refresh_deveRejeitar_quandoTokenExpirado` | Refresh token expirado é invalidado e rejeitado |
| `logout_deveRevogarRefreshTokens` | Logout apaga os refresh tokens do usuário |

### Integração — `AuthIntegrationTest`

Testam o stack completo: HTTP → Controller → Service → banco de dados real (MySQL).
Cada teste faz rollback automático via `@Transactional` e roda no profile `test`
(`@ActiveProfiles("test")`, ver `src/test/resources/application-test.yml`).

**Requer MySQL rodando em `localhost:3306`.**

| Teste | Endpoint | Cenário |
|---|---|---|
| `register_deveRetornar201EToken_quandoDadosValidos` | POST /auth/register | Dados válidos → 201 + access/refresh |
| `register_deveRetornar400_quandoEmailDuplicado` | POST /auth/register | Email já cadastrado → 400 + `{ error }` |
| `register_deveRetornar400ComErrosDeCampo_quandoDadosInvalidos` | POST /auth/register | Campos inválidos → 400 + erros por campo |
| `login_deveRetornar200EToken_quandoCredenciaisValidas` | POST /auth/login | Credenciais corretas → 200 + access/refresh |
| `login_deveRetornar401_quandoEmailNaoExiste` | POST /auth/login | Email inexistente → 401 + `{ error }` |
| `login_deveRetornar401_quandoSenhaErrada` | POST /auth/login | Senha errada → 401 + `{ error }` |
| `login_deveDarMesmaRespostaPraEmailESenhaErrados` | POST /auth/login | Mesma mensagem para email e senha inválidos |
| `me_deveRetornar200ComDadosDoUsuario_quandoAutenticado` | GET /auth/me | Token válido → 200 + dados do usuário |
| `me_deveRetornar403_quandoNaoAutenticado` | GET /auth/me | Sem token → 403 |
| `logout_deveRetornar204_quandoAutenticado` | POST /auth/logout | Token válido → 204 |
| `logout_deveRetornar403_quandoNaoAutenticado` | POST /auth/logout | Sem token → 403 |
| `refresh_deveRetornar200ComNovoToken_quandoRefreshTokenValido` | POST /auth/refresh | Refresh válido → 200 + novo par |
| `refresh_deveRetornar401_quandoRefreshTokenInvalido` | POST /auth/refresh | Refresh inexistente → 401 + `{ error }` |
| `refresh_deveRetornar401_aposLogout` | POST /auth/refresh | Refresh revogado pelo logout → 401 |

### Rodar os testes

```bash
# Só unitários
./gradlew :services:auth-service:test --tests "com.justdoit.auth.feature.auth.AuthServiceTest"

# Só integração (requer MySQL)
./gradlew :services:auth-service:test --tests "com.justdoit.auth.feature.auth.AuthIntegrationTest"

# Todos
./gradlew :services:auth-service:test
```
