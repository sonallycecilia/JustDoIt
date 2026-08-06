# 2. Autenticação — cadastro, login, refresh e logout

## 2.1 Cadastro

```mermaid
sequenceDiagram
    autonumber
    actor U as Usuário
    participant F as Frontend
    participant RL as RateLimitFilter
    participant AC as AuthController
    participant AS as AuthService
    participant MX as MxEmailVerifier
    participant DB as MySQL

    U->>F: preenche o formulário
    F->>RL: GET /auth/check-email?email=...
    RL->>AC: dentro do limite
    AC->>AS: checkEmail
    AS->>DB: existsByEmail
    AS->>MX: o domínio aceita correio?
    AS-->>F: 200 {registered, deliverable, available}
    Note over F: nunca 4xx aqui — o frontend decide a UX<br/>com o diagnóstico

    U->>F: confirma o cadastro
    F->>RL: POST /auth/register
    RL->>AC: dentro do limite
    AC->>AS: register
    AS->>DB: existsByEmail

    alt e-mail já cadastrado
        AS-->>F: 400 "Email já cadastrado"
    else e-mail livre
        AS->>AS: bcrypt na senha
        AS->>DB: INSERT users
        AS->>AS: issueTokens, rememberMe = false
        AS->>DB: INSERT refresh_token com SHA-256 do valor
        AS-->>F: 201 {accessToken, refreshToken, expiresIn}
    end
```

Conta nova nasce sempre com sessão curta — 12 h — equivalente ao "manter
conectado" desmarcado.

## 2.2 Login

```mermaid
sequenceDiagram
    autonumber
    participant F as Frontend
    participant RL as RateLimitFilter
    participant AS as AuthService
    participant DB as MySQL

    F->>RL: POST /auth/login {email, password, rememberMe}

    alt balde do IP vazio
        RL-->>F: 429 + Retry-After: 60
    else dentro do limite
        RL->>AS: login
        AS->>DB: findByEmail

        alt e-mail não existe
            AS->>AS: bcrypt contra hash sacrificial
            Note over AS: paga o mesmo custo de CPU de propósito —<br/>sem isso o tempo de resposta revelaria<br/>quais e-mails têm conta
            AS-->>F: 401 "Credenciais inválidas"
        else senha errada
            AS-->>F: 401 "Credenciais inválidas"
        else credenciais corretas
            AS->>DB: DELETE refresh_token WHERE user_id
            Note over AS,DB: um refresh token ativo por usuário:<br/>logar revoga as sessões anteriores
            AS->>AS: gera access token HS256, 15 min
            AS->>DB: INSERT refresh_token
            AS-->>F: 200 {accessToken, refreshToken, expiresIn}
        end
    end
```

A mensagem de erro é **a mesma** para e-mail inexistente e senha errada, e o
tempo de resposta também. As duas coisas juntas fecham o oráculo de enumeração
de contas — uma sozinha não resolve.

## 2.3 Refresh — rotação com detecção de reuso

Esta é a parte mais sutil do sistema. O refresh token é rotacionado a cada uso, e
um token já usado que reaparece pode ser **duas coisas muito diferentes**: roubo,
ou o cliente legítimo em corrida consigo mesmo.

```mermaid
flowchart TD
    start(["POST /auth/refresh<br/>{refreshToken}"]) --> hash["sha256 do valor recebido"]
    hash --> find{"existe linha com<br/>esse token_hash?"}

    find -- não --> rej1["401 — token inválido"]
    find -- sim --> exp{"expires_at<br/>já passou?"}

    exp -- sim --> del["DELETE da linha"] --> rej2["401 — token inválido"]
    exp -- não --> used{"used_at está nulo?"}

    used -- "sim, primeiro uso" --> mark["used_at = agora<br/><small>marca, não apaga: um reuso futuro<br/>precisa ser detectável</small>"] --> issue

    used -- "não, já foi usado" --> window{"usado há menos de<br/>30 s?"}

    window -- "sim — janela de graça" --> race["cliente legítimo em corrida:<br/>duas abas renovando juntas,<br/>ou F5 no meio de um refresh em voo"] --> issue

    window -- "não — reuso tardio" --> theft["cadeia comprometida<br/><small>padrão OAuth token-family</small>"]
    theft --> nuke["DELETE todos os refresh_token do usuário"] --> rej3["401 — força re-login em<br/>todos os dispositivos"]

    issue["emite novo par<br/>access + refresh"] --> inherit["herda rememberMe do token antigo<br/><small>sem isso, a sessão de 30 dias<br/>encolheria para 12 h no primeiro refresh</small>"]
    inherit --> ok["200 {accessToken, refreshToken}"]

    classDef okc fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef bad fill:#ffebee,stroke:#c62828,color:#b71c1c
    classDef warn fill:#fff3e0,stroke:#ef6c00,color:#e65100
    class ok,issue,inherit,mark okc
    class rej1,rej2,rej3,theft,nuke bad
    class race,window warn
```

### Por que a janela de 30 s existe

Sem ela, o cenário abaixo deslogava o usuário legítimo a cada ciclo de access
token:

```mermaid
sequenceDiagram
    participant A as Aba 1
    participant B as Aba 2
    participant S as auth-service

    Note over A,B: o access token de 15 min expirou nas duas
    A->>S: POST /auth/refresh (token R1)
    B->>S: POST /auth/refresh (token R1)
    S->>S: aba 1 chega primeiro: R1.used_at = agora, emite R2
    S-->>A: 200 (R2)
    S->>S: aba 2 chega 40 ms depois com R1 já usado
    Note over S: SEM a janela: interpretado como roubo,<br/>revoga tudo e desloga o usuário
    Note over S: COM a janela: emite R3 normalmente.<br/>O token órfão da corrida nunca é usado e expira só
    S-->>B: 200 (R3)
```

## 2.4 Prazos e logout

```mermaid
flowchart LR
    subgraph tokens["Os dois tokens"]
        at["<b>Access token</b><br/>JWT HS256, 15 min<br/><b>stateless</b> — não vai ao banco<br/>claims: sub, email, profile, type=access,<br/>iss=justdoit-auth-service, aud=justdoit-api, jti"]
        rt["<b>Refresh token</b><br/>32 bytes aleatórios em base64url<br/><b>persistido como SHA-256</b><br/>12 h, ou 30 dias com 'manter conectado'"]
    end

    subgraph fim["Fim de sessão"]
        lo["POST /auth/logout<br/>DELETE refresh_token do usuário"]
        job["RefreshTokenCleanupJob<br/>03:00 diariamente<br/>remove expirados e lápides"]
    end

    rt --> lo
    rt --> job

    classDef tok fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef end2 fill:#f3e5f5,stroke:#6a1b9a,color:#4a148c
    class at,rt tok
    class lo,job end2
```

**Por que só o refresh token é persistido.** O access token vale 15 min e é
verificado por assinatura — guardá-lo no banco só criaria uma consulta por
request sem ganho real. O refresh vale até 30 dias, então precisa ser revogável,
e por isso vive no banco. Guardado como **hash**: um dump da tabela
`refresh_token` não dá sessão a ninguém.

**Logout não invalida o access token.** Ele continua válido até expirar — no pior
caso 15 min. É a troca consciente que o modelo stateless impõe. O claim `jti` já
está no token justamente para permitir uma blacklist no futuro sem mudar o
contrato.
