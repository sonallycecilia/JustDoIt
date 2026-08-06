# 3. O caminho de uma request autenticada

Do clique no navegador até o banco. Este diagrama vale para **qualquer** endpoint
autenticado dos quatro serviços — a cadeia é a mesma, porque vem toda de
`libs/common`.

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant N as nginx
    participant CORS as CorsFilter
    participant JWT as JwtAuthFilter<br/>(libs/common)
    participant SEC as FilterSecurityInterceptor
    participant C as Controller
    participant V as Bean Validation
    participant S as Service
    participant R as Repository
    participant DB as MySQL

    F->>N: PATCH /tasks/{id}/complete<br/>Authorization: Bearer eyJ...
    N->>CORS: proxy para :8081<br/>+ X-Forwarded-For, X-Real-IP
    CORS->>JWT: origem permitida
    JWT->>JWT: lê o header Authorization

    alt sem header ou sem prefixo "Bearer "
        JWT->>SEC: segue sem autenticar
        SEC-->>F: 401 / 403
    else com Bearer
        JWT->>JWT: JwtValidator.validateToken
        Note over JWT: verifica assinatura HMAC, expiração,<br/>iss = justdoit-auth-service,<br/>aud = justdoit-api,<br/>type = access
        alt token inválido
            JWT->>SEC: segue sem autenticar
            SEC-->>F: 401 / 403
        else token válido
            JWT->>JWT: SecurityContext.authentication =<br/>UUID do claim sub
            JWT->>SEC: autenticado
            SEC->>C: rota liberada
            C->>C: @AuthenticationPrincipal UUID userId
            Note over C: o controller NÃO reprocessa o token —<br/>o userId já chega pronto
            C->>V: @Valid no corpo da request
            alt corpo inválido
                V-->>F: 400 com mapa campo → mensagem<br/>(GlobalExceptionHandler)
            else corpo válido
                C->>S: método de negócio + userId
                S->>R: findByIdAndUserId(id, userId)
                Note over S,R: o userId entra em TODA query.<br/>É o corte de propriedade: pedir o id de<br/>outra pessoa devolve 404, não 403
                R->>DB: SELECT ... WHERE id = ? AND user_id = ?
                DB-->>R: linha ou vazio
                alt não é do usuário / não existe
                    S-->>F: 404
                else é do usuário
                    S->>DB: UPDATE dentro de @Transactional
                    S-->>F: 200 com o DTO de resposta
                end
            end
        end
    end
```

## A cadeia de filtros, em ordem

```mermaid
flowchart LR
    req(["Request"]) --> cors["CorsFilter<br/><small>origens de CORS_ALLOWED_ORIGINS</small>"]
    cors --> rl["RateLimitFilter<br/><small>só no auth-service,<br/>só em login/register/check-email</small>"]
    rl --> jwt["JwtAuthFilter<br/><small>libs/common — igual nos 4 serviços</small>"]
    jwt --> authz["authorizeHttpRequests<br/><small>anyRequest().authenticated()</small>"]
    authz --> ctrl["Controller"]
    ctrl --> resp(["Response"])

    classDef f fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef only fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class cors,jwt,authz f
    class rl only
```

Configuração idêntica nos quatro serviços: CSRF desabilitado, `httpBasic` e
`formLogin` desabilitados, sessão `STATELESS`. Só o auth-service tem exceções
públicas — `/auth/register`, `/auth/login`, `/auth/refresh`, `/auth/check-email`.
Todo o resto, nos quatro serviços, exige token.

## Dois detalhes que valem citar

**`shouldNotFilterErrorDispatch` retorna `false`.** Por padrão o
`OncePerRequestFilter` do Spring não roda no dispatch interno para `/error`. Sem
essa sobrescrita, qualquer erro real — 400 de corpo inválido, 405 de método
errado — era re-despachado **sem autenticação**; como toda rota exige
autenticação, o erro voltava ao frontend mascarado como **403**. Rodando o filtro
também no `/error`, o status verdadeiro chega ao cliente.

**O `userId` nunca vem do cliente.** Não existe nenhum endpoint que aceite
`userId` como parâmetro de query ou campo de corpo. Ele vem sempre do claim `sub`
do token, injetado pelo filtro. É isso que torna impossível pedir os dados de
outra pessoa — não há onde escrever o id dela.
