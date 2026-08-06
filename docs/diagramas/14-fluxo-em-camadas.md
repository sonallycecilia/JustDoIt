# 14. Fluxo do código — o caminho pelas camadas

Se [13-rotas.md](13-rotas.md) é o *mapa das portas*, este é o *caminho de dentro*:
que classe chama qual, com nomes reais de método, do controller até o banco.

## 14.1 As camadas e o que cada uma pode fazer

```mermaid
flowchart TB
    subgraph pkg["com.justdoit.&lt;service&gt;"]
        direction TB

        ctrl["<b>Controller</b><br/><small>feature/&lt;nome&gt;/XController.java</small><br/><br/>recebe HTTP · valida com @Valid<br/>recebe o userId via @AuthenticationPrincipal<br/>traduz exceção em status HTTP<br/><br/><i>NÃO tem regra de negócio<br/>NÃO toca em repository</i>"]

        svc["<b>Service</b><br/><small>feature/&lt;nome&gt;/XService.java</small><br/><br/>regra de negócio · @Transactional<br/>checa propriedade pelo userId<br/>converte Entity em DTO<br/><br/><i>NÃO conhece HTTP<br/>NÃO devolve Entity para fora</i>"]

        repo["<b>Repository</b><br/><small>feature/&lt;nome&gt;/XRepository.java</small><br/><br/>interface JpaRepository<br/>query derivada do nome do método<br/>ou @Query em JPQL<br/><br/><i>NADA de lógica</i>"]

        ent["<b>Entity</b><br/><small>feature/&lt;nome&gt;/X.java</small><br/><br/>@Entity mapeada com JPA<br/>Lombok: @Data @Builder"]

        dto["<b>DTOs</b><br/><small>shared/XRequest.java · XResponse.java</small><br/><br/>Java <b>records</b> imutáveis<br/>anotações de Bean Validation<br/>@TextoSeguro nos textos livres"]

        cfg["<b>config/</b><br/><small>WebSecurityConfig · JwtUtil<br/>RateLimitFilter</small>"]

        intg["<b>integration/</b><br/><small>clientes RestClient para<br/>os outros serviços</small>"]
    end

    db[("MySQL")]

    ctrl --> svc --> repo --> ent --> db
    dto -. "entra e sai" .-> ctrl
    dto -. "o service monta" .-> svc
    svc -. "quando precisa de outro serviço" .-> intg

    classDef c fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    classDef s fill:#ede7f6,stroke:#4527a0,color:#311b92
    classDef r fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef d fill:#fff8e1,stroke:#ff8f00,color:#e65100
    class ctrl c
    class svc s
    class repo,ent,db r
    class dto,cfg,intg d
```

Cada serviço é organizado **por feature, não por camada**: em vez de pastas
`controllers/`, `services/`, `repositories/`, tudo que pertence a "tarefa" fica em
`feature/task/` — controller, service, repository e entity juntos. É por isso que
`feature/timer/` tem 7 arquivos e `feature/task/` tem 9: cada pasta é uma
capacidade completa.

O que é **compartilhado pelos quatro serviços** mora em `libs/common`:

```mermaid
flowchart LR
    common["<b>libs/common</b>"]
    common --> jv["security/JwtValidator<br/><small>valida assinatura, iss, aud, type</small>"]
    common --> jf["security/JwtAuthFilter<br/><small>põe o UUID no SecurityContext</small>"]
    common --> ts["validation/TextoSeguro<br/>validation/TextoSeguroValidator"]
    common --> geh["web/GlobalExceptionHandler<br/>web/ErrorResponse"]

    classDef l fill:#fff8e1,stroke:#ff8f00,color:#e65100
    class common,jv,jf,ts,geh l
```

## 14.2 Trilha completa — criar uma tarefa

`POST /tasks` do começo ao fim, com nome de classe e de método.

```mermaid
sequenceDiagram
    autonumber
    participant HTTP as Requisição HTTP
    participant JF as JwtAuthFilter<br/><small>libs/common</small>
    participant TR as TaskRequest<br/><small>record em shared/</small>
    participant TC as TaskController<br/><small>feature/task/</small>
    participant TS as TaskService<br/><small>feature/task/</small>
    participant CR as CategoryRepository<br/><small>feature/category/</small>
    participant T as Task<br/><small>@Entity</small>
    participant TRP as TaskRepository
    participant DB as MySQL
    participant RSP as TaskResponse<br/><small>record em shared/</small>

    HTTP->>JF: POST /tasks<br/>Bearer + JSON
    JF->>JF: validateToken, extractUserId
    JF->>JF: SecurityContext = UUID do claim sub

    JF->>TR: Jackson desserializa o JSON no record
    TR->>TR: Bean Validation<br/><small>@NotBlank @Size(200) @TextoSeguro no title<br/>@Size(5000) @TextoSeguro na description</small>

    alt validação falha
        TR-->>HTTP: 400 pelo GlobalExceptionHandler<br/>{campo: mensagem}
    else validação passa
        TR->>TC: createTask(request, userId)
        Note over TC: @AuthenticationPrincipal UUID userId<br/>injetado pelo Spring Security

        TC->>TS: taskService.createTask(request, userId)

        rect rgb(237, 231, 246)
        Note over TS,DB: @Transactional

        opt request.categoryId() não é nulo
            TS->>CR: findByIdAndUserId(categoryId, userId)
            alt categoria não é do usuário
                CR-->>TS: Optional.empty
                TS-->>TC: IllegalArgumentException<br/>"Category not found"
            end
        end

        TS->>T: Task.builder()<br/>.userId(userId).category(category)<br/>.title().description().dueDate().dueTime()<br/>.priority(ou NORMAL).status(PENDING)<br/>.build()
        Note over T: o userId vem do PARÂMETRO,<br/>que veio do token — nunca do request

        TS->>TRP: save(task)
        TRP->>DB: INSERT INTO task
        DB-->>TRP: linha com id gerado
        end

        TS->>RSP: toResponse(task)<br/><small>método privado do service</small>
        Note over RSP: nunca devolve a Entity:<br/>o record expõe só o que o cliente precisa,<br/>e resolve o cycleType do cycleConfig

        RSP-->>TC: TaskResponse
        TC-->>HTTP: 201 Created + JSON
    end
```

## 14.3 A mesma trilha, resumida em uma linha por camada

Este é o formato que funciona melhor para falar em voz alta:

| Camada | Classe | O que faz aqui |
|---|---|---|
| Filtro | `JwtAuthFilter` | valida o token e coloca o `UUID` no `SecurityContext` |
| DTO de entrada | `TaskRequest` (record) | Jackson preenche, Bean Validation aprova ou rejeita |
| Controller | `TaskController.createTask` | recebe `@Valid` + `@AuthenticationPrincipal`, delega |
| Service | `TaskService.createTask` | abre transação, valida a categoria, monta a `Task` |
| Entity | `Task` | objeto mapeado com JPA |
| Repository | `TaskRepository.save` | Spring Data gera o `INSERT` |
| DTO de saída | `TaskResponse` (record) | `toResponse` converte Entity → record |
| Controller | `TaskController` | devolve `201` |

## 14.4 Trilha com integração — concluir tarefa

O mesmo caminho, mais um salto entre serviços. O que muda: aparece um **evento de
domínio** e um **cliente HTTP**.

```mermaid
sequenceDiagram
    autonumber
    participant TC as TaskController
    participant TS as TaskService
    participant TRP as TaskRepository
    participant EP as ApplicationEventPublisher<br/><small>do Spring</small>
    participant EV as TaskCompletedEvent<br/><small>record em feature/task/</small>
    participant TL as TaskCompletedListener<br/><small>integration/</small>
    participant NC as NotificationClient<br/><small>integration/</small>
    participant NS as notification-service

    TC->>TS: completeTask(id, userId, authHeader)

    rect rgb(237, 231, 246)
    Note over TS,TRP: @Transactional
    TS->>TRP: findByIdAndUserId(id, userId)
    TS->>TS: setStatus(COMPLETED)<br/>setCompletedAt(now)
    TS->>TRP: save(task)
    TS->>EP: publishEvent(new TaskCompletedEvent(...))
    end

    Note over EP: COMMIT da transação

    EP->>TL: @TransactionalEventListener(AFTER_COMMIT)
    TL->>NC: notifyTaskCompleted(authHeader, taskId, title)
    NC->>NS: RestClient POST /notifications
    Note over NC: try/catch engole a falha:<br/>log.warn e segue
    TC-->>TC: 200 TaskResponse
```

**Por que um evento em vez de chamar o client direto do service.** Chamando
direto, o `TaskService` passaria a conhecer o notification-service e a chamada HTTP
aconteceria **dentro** da transação. Com o evento, o service só anuncia "a tarefa
foi concluída" e quem escuta está em `integration/` — a camada de negócio fica sem
dependência de rede, e o `AFTER_COMMIT` garante que a notificação só sai se o
commit acontecer.

## 14.5 Trilha de autenticação — login

O login é o único fluxo onde o service faz criptografia em vez de só orquestrar.

```mermaid
sequenceDiagram
    autonumber
    participant HTTP as POST /auth/login
    participant RL as RateLimitFilter<br/><small>config/</small>
    participant LR as LoginRequest<br/><small>record em shared/</small>
    participant AC as AuthController
    participant AS as AuthService
    participant PE as PasswordEncoder<br/><small>BCrypt, bean do WebSecurityConfig</small>
    participant UR as UserRepository
    participant JU as JwtUtil<br/><small>config/</small>
    participant RTR as RefreshTokenRepository

    HTTP->>RL: balde de fichas do IP
    RL->>LR: dentro do limite
    LR->>AC: @Valid LoginRequest
    AC->>AS: login(request)

    AS->>UR: findByEmail(email)
    alt não achou
        AS->>PE: matches(senha, dummyPasswordHash)
        Note over PE: hash sacrificial criado no @PostConstruct:<br/>gasta o mesmo tempo de CPU de propósito
        AS-->>AC: IllegalArgumentException
        AC-->>HTTP: 401
    else achou
        AS->>PE: matches(senha, user.getPasswordHash())
        alt senha errada
            AS-->>AC: IllegalArgumentException
            AC-->>HTTP: 401
        else senha correta
            AS->>RTR: deleteByUserId(user.getId())
            AS->>JU: generateAccessToken(id, email, "USER")
            JU-->>AS: JWT HS256, 15 min
            AS->>AS: generateRefreshTokenValue()<br/><small>SecureRandom, 32 bytes, base64url</small>
            AS->>RTR: save(RefreshToken com sha256 do valor)
            AS-->>AC: AuthResponse
            AC-->>HTTP: 200 {accessToken, refreshToken, expiresIn}
        end
    end
```

## 14.6 O padrão que se repete em TODA operação

Se a professora perguntar "e como funciona a rota X?", a resposta é sempre esta
forma — só mudam os nomes:

```mermaid
flowchart TD
    a["<b>1.</b> JwtAuthFilter valida o token<br/>e põe o userId no SecurityContext"]
    b["<b>2.</b> O record de Request é preenchido<br/>e validado — 400 se falhar"]
    c["<b>3.</b> O Controller recebe<br/>@Valid + @AuthenticationPrincipal UUID userId<br/>e só delega"]
    d["<b>4.</b> O Service abre @Transactional e busca<br/><b>findByIdAndUserId(id, userId)</b>"]
    e{"achou?"}
    f["IllegalArgumentException<br/>→ o Controller traduz em <b>404</b>"]
    g["<b>5.</b> Aplica a regra e grava<br/>pelo Repository"]
    h["<b>6.</b> toResponse converte Entity → record<br/><small>a Entity nunca sai do service</small>"]
    i["<b>7.</b> Controller devolve<br/>200 / 201 / 204"]

    a --> b --> c --> d --> e
    e -- não --> f
    e -- sim --> g --> h --> i

    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef bad fill:#ffebee,stroke:#c62828,color:#b71c1c
    class a,b,c,d,g,h,i ok
    class f bad
```

As três invariantes que sustentam a segurança do sistema, e que valem repetir:

1. **O `userId` sempre vem do token**, nunca de parâmetro, query ou corpo. Não
   existe onde escrever o id de outra pessoa.
2. **Toda busca é `findByIdAndUserId`**, nunca `findById` puro. O corte de
   propriedade está na query, não num `if` depois.
3. **A Entity nunca atravessa a fronteira do service.** Sai sempre um `record` de
   Response, montado por um `toResponse` privado.

## 14.7 Onde estão as exceções a esse padrão

Honestidade sobre o que não segue a forma acima:

| Caso | Por que difere |
|---|---|
| `NotificationService.markAsRead` | usa `findById(...).filter(n -> n.getUserId().equals(userId))` em vez de query composta — mesmo efeito, checagem em memória |
| `TaskTimerService.start` | a garantia é o **índice único no banco**, não a query; o `if` prévio é só atalho ([06](06-cronometro.md)) |
| `TaskTimerService.somarSegundos` | `UPDATE ... += n` direto, sem carregar a entidade — precisa ser atômico |
| `PinnedNoteCompatController` | rota `/me/note` mantida por compatibilidade com o frontend; delega para a mesma `NoteService` |
| `UserNoteMigrationRunner` | roda no boot, migra `user_note` → `note`; idempotente. Não é request |
| Jobs `@Scheduled` | entram pelo service sem controller e sem usuário ([09](09-jobs-agendados.md)) |
