# 11. Validação de entrada — barrando texto malicioso

Todo campo de texto livre que o usuário preenche passa por `@TextoSeguro`, uma
constraint de Bean Validation que vive em `libs/common` e é reusada pelos quatro
serviços.

## 11.1 O fluxo

```mermaid
flowchart TD
    req(["POST /tasks<br/>{title, description, ...}"]) --> valid["Bean Validation no @Valid do controller"]

    valid --> nb{"@NotBlank<br/>está preenchido?"}
    nb -- não --> e1["400 — campo obrigatório"]
    nb -- sim --> sz{"@Size<br/>dentro do limite?"}
    sz -- não --> e2["400 — tamanho excedido"]
    sz -- sim --> ts["@TextoSeguro<br/>TextoSeguroValidator.isValid"]

    ts --> nulo{"nulo ou em branco?"}
    nulo -- sim --> pass["<b>válido</b><br/><small>quem exige preenchimento é o @NotBlank.<br/>Separar evita mensagem enganosa de<br/>'código malicioso' num campo vazio</small>"]
    nulo -- não --> match{"casa com algum dos<br/>padrões perigosos?"}

    match -- sim --> rej["400 pelo GlobalExceptionHandler<br/>'Conteúdo não permitido:<br/>possível código malicioso'<br/><b>nada é persistido</b>"]
    match -- não --> pass

    pass --> svc["Service → Repository → banco"]

    classDef bad fill:#ffebee,stroke:#c62828,color:#b71c1c
    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    class e1,e2,rej bad
    class pass,svc ok
```

## 11.2 O que é rejeitado

```mermaid
flowchart LR
    subgraph xss["XSS / injeção de HTML"]
        p1["<code>&lt;/?[a-zA-Z]</code><br/><small>abertura de tag com o nome COLADO</small>"]
        p2["handlers inline<br/><small>onerror, onload, onclick, onmouseover,<br/>onmouseout, onfocus, onblur, onsubmit,<br/>onchange, onkeydown, onkeypress seguidos de =</small>"]
        p3["URIs executáveis<br/><small>javascript: · vbscript: · data:text/html</small>"]
    end

    subgraph expr["Injeção de expressão"]
        p4["<code>${</code> — SpEL, JNDI/Log4Shell"]
        p5["<code>#{</code> — EL"]
    end

    subgraph sql["SQL — defesa em profundidade"]
        p6["comandos completos<br/><small>union select, drop table, delete from,<br/>insert into, update X set</small>"]
        p7["tautologia clássica<br/><small>1' OR '1'='1</small>"]
        p8["comentário após aspa e comando encadeado<br/><small>admin'-- · ; DROP</small>"]
    end

    subgraph outros["Outros"]
        p9["byte nulo <code>\x00</code><br/><small>truncamento em camada nativa</small>"]
    end

    classDef p fill:#ffebee,stroke:#c62828,color:#b71c1c
    class p1,p2,p3,p4,p5,p6,p7,p8,p9 p
```

## 11.3 O que **não** é rejeitado, e por quê

Esta é a parte mais importante de explicar: a lista é deliberadamente
**específica**, não genérica. O objetivo é **zero falso positivo em português
legítimo**.

```mermaid
flowchart LR
    subgraph aceito["Texto legítimo que PASSA"]
        a1["<code>orçamento &lt; 500</code><br/><small>'&lt;' seguido de número não é tag</small>"]
        a2["<code>a &lt; b</code><br/><small>'&lt; ' com espaço: navegador não interpreta<br/>'&lt; script' como tag</small>"]
        a3["<code>D'Ávila</code><br/><small>apóstrofo isolado</small>"]
        a4["<code>--</code> isolado, <code>&amp; % +</code>"]
        a5["<code>onde = ...</code><br/><small>a lista de handlers é fechada, não regex<br/>genérica de palavra terminada em 'on'</small>"]
    end

    subgraph excecoes["Campos que NÃO recebem @TextoSeguro"]
        b1["<b>password</b><br/><small>senha legitimamente contém &lt; ' ;<br/>barrá-los enfraqueceria a política de senhas</small>"]
        b2["<b>email</b><br/><small>formato já validado por @Email</small>"]
        b3["<b>color</b> da categoria<br/><small>formato restrito #RRGGBB</small>"]
    end

    classDef ok fill:#e8f5e9,stroke:#2e7d32,color:#1b5e20
    classDef exc fill:#fff8e1,stroke:#ff8f00,color:#e65100
    class a1,a2,a3,a4,a5 ok
    class b1,b2,b3 exc
```

## 11.4 Onde a constraint está aplicada

```mermaid
flowchart TB
    ts["<b>@TextoSeguro</b><br/><small>libs/common/validation</small>"]

    ts --> a["<b>auth-service</b><br/>RegisterRequest.name<br/>UpdateProfileRequest.name<br/>UpdateProfileRequest.avatarUrl"]
    ts --> t["<b>task-service</b><br/>TaskRequest.title · .description<br/>SubTaskRequest.title<br/>CategoryRequest.name · .description<br/>NoteRequest.title · .content<br/>MeNoteRequest.content<br/>TaskNoteRequest.content"]
    ts --> n["<b>notification-service</b><br/>CreateNotificationRequest.title · .message"]

    classDef lib fill:#fff8e1,stroke:#ff8f00,color:#e65100
    classDef srv fill:#e3f2fd,stroke:#1565c0,color:#0d47a1
    class ts lib
    class a,t,n srv
```

## Decisões de política

**Rejeitar, não sanitizar.** Zero Trust: um 400 explícito em vez de limpar o
conteúdo em silêncio. Sanitizar alteraria o que o usuário escreveu sem ele
perceber — e um filtro de sanitização mal-feito é uma falsa sensação de
segurança. O usuário vê o erro e corrige.

**SQL Injection já é estruturalmente impossível.** Todo acesso a dados é via
Spring Data com query parametrizada — não existe query nativa concatenada no
projeto. Os padrões de SQL em `@TextoSeguro` são **defesa em profundidade**, não a
proteção principal. Isso vale dizer explicitamente: a proteção real é a
arquitetura de acesso a dados, o validador é a segunda camada.

**A responsabilidade é separada.** `@NotBlank` cuida de preenchimento, `@Size` de
tamanho, `@TextoSeguro` de conteúdo perigoso. Um campo vazio nunca recebe a
mensagem "possível código malicioso".

Há testes de qualidade cobrindo isso em cada serviço
(`ValidacaoEntradaMetricsTest`), e as métricas estão em
[`docs/METRICAS-QUALIDADE.md`](../METRICAS-QUALIDADE.md) e
[`docs/EVIDENCIAS-QUALIDADE.md`](../EVIDENCIAS-QUALIDADE.md).
