# auth-service — Implementação e Testes

## Endpoints

| Método | Rota | Auth | Descrição |
|---|---|---|---|
| POST | `/auth/register` | Não | Cadastrar novo usuário |
| POST | `/auth/login` | Não | Autenticar e receber token |
| POST | `/auth/logout` | Sim | Invalidar token do usuário |
| GET | `/auth/me` | Sim | Retornar dados do usuário logado |

---

## Fluxo de registro

```
POST /auth/register
  → valida campos (Bean Validation)
  → verifica email duplicado
  → salva usuário com senha em BCrypt
  → gera token JWT (24h, HS256)
  → persiste token na tabela jwt_token
  → retorna { token }
```

## Fluxo de login

```
POST /auth/login
  → valida campos
  → busca usuário pelo email
  → compara senha com BCrypt
  → apaga tokens antigos do usuário (deleteByUserId)
  → gera novo token JWT
  → persiste na tabela jwt_token
  → retorna { token }
```

---

## DTOs

### `RegisterRequest`
```json
{
  "name": "string (obrigatório)",
  "email": "string válido (obrigatório)",
  "password": "string mínimo 8 chars (obrigatório)",
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

### `AuthResponse`
```json
{ "token": "eyJ..." }
```

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
| Login bem-sucedido | 200 OK |
| Logout bem-sucedido | 204 No Content |
| Campos inválidos | 400 Bad Request + erros por campo |
| Email duplicado | 400 Bad Request + `{ "error": "Email already registered" }` |
| Credenciais erradas | 401 Unauthorized + `{ "error": "Invalid credentials" }` |
| Sem token | 403 Forbidden |

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

### `JwtToken` — tabela `jwt_token`
| Campo | Tipo | Detalhe |
|---|---|---|
| id | UUID | gerado pelo Hibernate |
| token | String | até 2048 chars |
| user_id | UUID | referência ao usuário |
| email | String | |
| profile | String | fixo `"USER"` |
| expires_at | LocalDateTime | now + 24h |

---

## Segurança

- Rotas públicas: apenas `/auth/register` e `/auth/login`
- Todas as demais rotas (incluindo `/auth/logout` e `/auth/me`) exigem token válido
- `JwtAuthFilter` valida a assinatura do token a cada requisição
- Controllers extraem o `userId` direto do token via `JwtUtil.extractUserId()` — sem consultar o `SecurityContextHolder`
- Sessão stateless, CSRF desabilitado, CORS liberado para `localhost:3000`

---

## Testes

### Unitários — `AuthServiceTest`

Testam o `AuthService` isolado com mocks (sem banco, sem Spring context).

| Teste | Cenário |
|---|---|
| `register_deveRetornarToken_quandoDadosValidos` | Registro com dados corretos retorna token |
| `register_deveLancarExcecao_quandoEmailJaExiste` | Email duplicado lança exceção e não salva no banco |
| `register_deveSalvarSenhaComoHash` | Senha é salva como hash BCrypt, nunca em texto puro |
| `login_deveRetornarToken_quandoCredenciaisValidas` | Login com credenciais corretas retorna token |
| `login_deveLancarExcecao_quandoEmailNaoExiste` | Email inexistente lança exceção |
| `login_deveLancarExcecao_quandoSenhaErrada` | Senha incorreta lança exceção e não gera token |
| `login_deveDarMesmoErroPraEmailESenhaErrados` | Mesma mensagem de erro para email e senha inválidos (não vaza qual campo falhou) |

### Integração — `AuthIntegrationTest`

Testam o stack completo: HTTP → Controller → Service → banco de dados real (MySQL).
Cada teste faz rollback automático via `@Transactional` — nada persiste entre testes.

**Requer MySQL rodando em `localhost:3306`.**

| Teste | Endpoint | Cenário |
|---|---|---|
| `register_deveRetornar201EToken_quandoDadosValidos` | POST /auth/register | Dados válidos → 201 + token |
| `register_deveRetornar400_quandoEmailDuplicado` | POST /auth/register | Email já cadastrado → 400 + `{ error }` |
| `register_deveRetornar400ComErrosDeCampo_quandoDadosInvalidos` | POST /auth/register | Campos inválidos → 400 + erros por campo |
| `login_deveRetornar200EToken_quandoCredenciaisValidas` | POST /auth/login | Credenciais corretas → 200 + token |
| `login_deveRetornar401_quandoEmailNaoExiste` | POST /auth/login | Email inexistente → 401 + `{ error }` |
| `login_deveRetornar401_quandoSenhaErrada` | POST /auth/login | Senha errada → 401 + `{ error }` |
| `login_deveDarMesmaRespostaPraEmailESenhaErrados` | POST /auth/login | Mesma mensagem para email e senha inválidos |
| `me_deveRetornar200ComDadosDoUsuario_quandoAutenticado` | GET /auth/me | Token válido → 200 + dados do usuário |
| `me_deveRetornar403_quandoNaoAutenticado` | GET /auth/me | Sem token → 403 |
| `logout_deveRetornar204_quandoAutenticado` | POST /auth/logout | Token válido → 204 |
| `logout_deveRetornar403_quandoNaoAutenticado` | POST /auth/logout | Sem token → 403 |

### Rodar os testes

```bash
# Só unitários
./gradlew :services:auth-service:test --tests "com.justdoit.auth.feature.auth.AuthServiceTest"

# Só integração (requer MySQL)
./gradlew :services:auth-service:test --tests "com.justdoit.auth.feature.auth.AuthIntegrationTest"

# Todos
./gradlew :services:auth-service:test
```
