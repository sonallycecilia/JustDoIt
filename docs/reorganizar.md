# Prompt — Reorganização estrutural do projeto JustDoIt

## Contexto

Você é um agente de refatoração estrutural. O projeto **JustDoIt** é um monorepo Gradle com múltiplos subprojetos (microserviços) escritos em **Kotlin com Gradle Kotlin DSL (`.kts`)**. A estrutura atual organiza os arquivos por camada técnica (`controller/`, `service/`, `repository/`, `model/`). A meta é migrar para uma organização **por feature**, além de limpar a raiz do repositório.

---

## O que você deve fazer

### 1. Reorganizar internamente cada serviço

Para cada um dos quatro serviços abaixo, aplique a mesma lógica:

- `auth-service`
- `task-service`
- `schedule-service`
- `notification-service`

**Estrutura interna alvo de cada serviço:**

```
<serviço>/
└── src/
    └── main/
        └── kotlin/
            └── com/justdoit/<serviço>/
                ├── feature/
                │   ├── <feature-a>/
                │   │   ├── <FeatureA>Controller.kt
                │   │   ├── <FeatureA>Service.kt
                │   │   ├── <FeatureA>Repository.kt
                │   │   └── <FeatureA>.kt           ← entidade/model
                │   └── <feature-b>/
                │       └── ...
                ├── config/                          ← beans, security, etc.
                ├── exception/                       ← handlers, classes de erro
                └── shared/                          ← DTOs e utilitários comuns
```

**Regras para identificar as features:**
- Analise os arquivos existentes em cada serviço
- Agrupe por domínio/responsabilidade (ex: no auth-service: `user`, `token`, `password`)
- Cada feature deve conter todos os artefatos relacionados a ela (controller + service + repository + model/entity)
- Arquivos que servem múltiplas features vão em `shared/` ou `config/`

**Para cada arquivo movido:**
- Atualize a declaração `package` no topo do arquivo para refletir o novo caminho
- Atualize todos os `import` nos demais arquivos que referenciam o arquivo movido
- Não altere a lógica de negócio, apenas os pacotes e imports

---

### 2. Reorganizar a raiz do repositório

**Estrutura alvo da raiz:**

```
justDoIt/
├── docs/                        ← todos os arquivos .md de documentação
│   ├── architecture.md          ← renomeado de ARCHITETURE.md
│   ├── estrutura.md             ← renomeado de ESTRUTURA.md
│   ├── contributing.md          ← renomeado de CONTRIBUITING.md
│   └── help.md                  ← renomeado de HELP.md
├── infra/                       ← arquivos de infraestrutura
│   └── docker-compose.yml
├── scripts/                     ← scripts utilitários (se houver)
├── .claude/                     ← mantém onde está
├── auth-service/
├── task-service/
├── schedule-service/
├── notification-service/
├── frontend/
├── build.gradle.kts
├── settings.gradle.kts
├── gradlew
├── gradlew.bat
├── .gitignore
├── .gitattributes
└── README.md
```

**Regras:**
- Mova todos os `.md` (exceto `README.md`) para `docs/`, renomeando para lowercase sem underline
- Mova `docker-compose.yml` para `infra/`
- `README.md`, `LICENSE`, `gradlew`, `gradlew.bat`, `build.gradle.kts`, `settings.gradle.kts`, `.gitignore`, `.gitattributes` permanecem na raiz
- Se `build.gradle.kts` ou `settings.gradle.kts` referenciam caminhos que foram movidos, atualize-os

---

### 3. Verificar e atualizar o build.gradle

- Verifique se algum `build.gradle.kts` (raiz ou de subprojeto) referencia caminhos de arquivos que foram movidos
- Atualize quaisquer referências de path quebradas
- Verifique se os `include()` no `settings.gradle.kts` ainda estão corretos após a reorganização
- Não altere dependências, versões ou plugins — apenas paths

---

## Ordem de execução recomendada

1. Faça um levantamento completo: liste todos os arquivos de cada serviço antes de mover qualquer coisa
2. Reorganize a raiz primeiro (mais seguro, sem impacto em código)
3. Reorganize um serviço de cada vez, na ordem: `auth-service` → `task-service` → `schedule-service` → `notification-service`
4. Para cada serviço: mova os arquivos, atualize packages, atualize imports
5. Verifique o `build.gradle.kts` ao final de cada serviço
6. Ao final, rode `./gradlew build` para confirmar que tudo compila

---

## Restrições

- **Não altere lógica de negócio** — apenas estrutura de pastas, declarações de `package` e `import`
- **Não altere dependências** no `build.gradle.kts`
- **Não delete arquivos** — apenas mova e renomeie
- Se encontrar ambiguidade em qual feature um arquivo pertence, agrupe em `shared/` e deixe um comentário `// TODO: mover para feature específica quando definido`
- Se um arquivo não existir ainda (pasta vazia), crie apenas o diretório, sem criar arquivos fictícios
