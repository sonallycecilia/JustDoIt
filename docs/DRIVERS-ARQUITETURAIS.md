# Drivers Arquiteturais — JustDoIt

> Documento de arquitetura. Última revisão: 2026-07-28.

Este documento registra os **drivers arquiteturais** do JustDoIt: as forças que
determinaram a estrutura do sistema. Cada driver é rastreado até o mecanismo que o
implementa no código e até a evidência que prova que ele continua valendo.

---

## 0. Como ler este documento

Um driver arquitetural **não é código**. O que existe no repositório são três
coisas *derivadas* dele:

| Elemento | O que é | Onde vive |
|---|---|---|
| **Driver** | A força/exigência que restringe a decisão | Este documento |
| **Tática** | A estratégia escolhida para atender ao driver | Este documento (coluna "Tática") |
| **Mecanismo** | A classe, filtro ou config que implementa a tática | `src/main/**` |
| **Evidência** | O teste que falha se alguém quebrar a decisão | `src/test/**` |

Um atributo de qualidade só é um driver de verdade quando é **mensurável**. Por
isso cada um abaixo está no formato de **cenário de 6 partes**
(fonte, estímulo, ambiente, artefato, resposta, medida da resposta) — a "medida"
é justamente o que vira teste.

---

## 1. Requisitos funcionais primários

Nem todo caso de uso é driver. Os primários são os que **moldam a estrutura** —
se fossem removidos, a arquitetura seria outra.

| ID | Caso de uso primário | Impacto estrutural |
|---|---|---|
| RF-01 | Autenticar usuário e emitir credencial válida para todo o sistema | Justifica um serviço dedicado (`auth-service`) como **único emissor** de token, e a validação distribuída em `libs/common` |
| RF-02 | Gerenciar tarefas com módulos opcionais (timer, foco, ciclo, notas, subtarefas) | Justifica `Task` como **aggregate root** e o layout `feature/<nome>` do task-service — cada módulo é um pacote independente pendurado no agregado |
| RF-03 | Planejar a semana em blocos de tempo (*time-blocking*) | Justifica o `schedule-service` separado: ciclo de vida (plano semanal `OPEN`/`CLOSED`) diferente do ciclo de vida da tarefa |
| RF-04 | Consolidar o esforço real da semana no resumo semanal | Justifica a **integração síncrona** schedule → task (`GET /tasks/report`), o único acoplamento entre domínios de negócio |
| RF-05 | Avisar o usuário sobre conclusão e atraso de tarefas | Justifica o `notification-service` e o padrão **best-effort** de integração |
| RF-06 | Excluir a conta e purgar todos os dados do usuário (LGPD) | Justifica o canal interno auth → task (`DELETE /me/data`), fora do roteamento público |
| RF-07 | Exportar os dados do usuário em CSV/JSON (LGPD, portabilidade) | Justifica `feature.export` e a rota `/me/export` |

> O layout `feature/<nome>` é a materialização direta de RF-02: **um caso de uso
> primário = um pacote**. Foi essa a motivação do refactor descrito em
> `docs/PLANO-REFACTOR-ARQUITETURA.md`.

---

## 2. Atributos de qualidade

### QA-01 — Segurança: resistência a credential stuffing e enumeração de e-mails

| Parte | Valor |
|---|---|
| **Fonte** | Atacante automatizado, externo, não autenticado |
| **Estímulo** | Rajada de tentativas de login / registro / verificação de e-mail |
| **Ambiente** | Produção (VPS), operação normal |
| **Artefato** | `auth-service` — `/auth/login`, `/auth/register`, `/auth/check-email` |
| **Resposta** | Requisições acima do limite são rejeitadas antes de tocar o banco |
| **Medida** | ≤ 20 req/min por IP (configurável); excedente recebe HTTP 429 + `Retry-After: 60` |

- **Tática:** limitar exposição — *token bucket* por IP.
- **Mecanismo:** `services/auth-service/src/main/java/com/justdoit/auth/config/RateLimitFilter.java`
- **Evidência:** `services/auth-service/src/test/java/com/justdoit/auth/config/RateLimitFilterTest.java`
- **Trade-off registrado:** `/auth/refresh` foi **deliberadamente excluído** do limite.
  Ele já é protegido pelo próprio refresh token e é chamado a cada ciclo de access
  token por todas as abas abertas — um 429 ali derrubava a sessão do usuário
  legítimo sem barrar ataque nenhum.
- **Limite conhecido:** o estado do balde é local ao processo. Ver RSC-04 e RISCO-01.

### QA-02 — Disponibilidade: falha de um serviço não derruba o serviço que o chama

| Parte | Valor |
|---|---|
| **Fonte** | Serviço interno dependente (notification-service, task-service) |
| **Estímulo** | Serviço fora do ar, lento ou retornando erro |
| **Ambiente** | Produção, operação degradada |
| **Artefato** | `NotificationClient` (task → notification), `TaskReportClient` (schedule → task) |
| **Resposta** | O chamador conclui a operação de negócio; a parte dependente é omitida e a falha é logada |
| **Medida** | Nenhuma request do usuário falha por indisponibilidade de dependência; latência adicional máxima de 2s (connect) + 3s (read) por chamada |

- **Tática:** isolamento de falha (*best-effort*) + timeouts curtos.
- **Mecanismos:**
  - `services/task-service/src/main/java/com/justdoit/task/integration/NotificationClient.java` — timeouts 2s/3s, exceção logada e engolida
  - `services/schedule-service/src/main/java/com/justdoit/schedule/integration/TaskReportClient.java` — falha vira `Optional.empty()`; o resumo semanal sai só com os dados locais (planejado, sem o realizado)
- **Trade-off registrado:** aceita-se **perda de notificação** e **resumo semanal
  incompleto** em troca de não acoplar a disponibilidade dos serviços. Não há
  retry nem fila — a entrega não é garantida por decisão.

### QA-03 — Consistência: nenhum efeito colateral externo sobre transação revertida

| Parte | Valor |
|---|---|
| **Fonte** | Usuário concluindo uma tarefa |
| **Estímulo** | Conclusão da tarefa cuja transação sofre rollback depois |
| **Ambiente** | Produção, operação normal |
| **Artefato** | `task-service` → `notification-service` |
| **Resposta** | Nenhuma notificação é emitida para uma conclusão que não foi persistida |
| **Medida** | Zero notificações falsas: o envio ocorre estritamente após o commit |

- **Tática:** publicação de evento de domínio na fase pós-commit.
- **Mecanismo:** `services/task-service/src/main/java/com/justdoit/task/integration/TaskCompletedListener.java`
  — `@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)`
- **Trade-off:** se o processo cair entre o commit e o envio, a notificação é
  perdida silenciosamente. Consistente com QA-02 (entrega best-effort).

### QA-04 — Modificabilidade: regra de segurança muda em um lugar só

| Parte | Valor |
|---|---|
| **Fonte** | Desenvolvedor |
| **Estímulo** | Mudança na regra de validação do token (claims, issuer, audience, expiração) |
| **Ambiente** | Tempo de desenvolvimento |
| **Artefato** | Os 4 serviços |
| **Resposta** | A mudança é aplicada em um único módulo e propagada por rebuild |
| **Medida** | 1 arquivo alterado, 0 serviços alterados; sem risco de divergência entre serviços |

- **Tática:** encapsular + módulo compartilhado.
- **Mecanismo:** `libs/common` — `JwtValidator`, `JwtAuthFilter`,
  `GlobalExceptionHandler`, com `ISSUER`/`AUDIENCE` como fonte única de verdade.
- **Evidência:** `libs/common/src/test/java/com/justdoit/common/security/JwtValidatorTest.java`
- **Contra-decisão deliberada:** o `WebSecurityConfig` **não** foi compartilhado.
  O do auth-service difere de verdade (permitAll em `/auth/**`, RateLimitFilter) e
  o do notification-service trata `/internal/notifications` via `X-Internal-Token`
  — unificar exigiria autoconfiguração condicional que não paga o custo.
- **Motivação original:** antes do refactor, essas classes estavam **copiadas nos
  4 serviços** (`docs/PLANO-REFACTOR-ARQUITETURA.md`).

### QA-05 — Modificabilidade: um novo módulo de tarefa não toca o núcleo

| Parte | Valor |
|---|---|
| **Fonte** | Desenvolvedor / produto |
| **Estímulo** | Adicionar um novo módulo opcional à tarefa (ex.: anexos, checklist) |
| **Ambiente** | Tempo de desenvolvimento |
| **Artefato** | `task-service` |
| **Resposta** | O módulo nasce como pacote novo, sem alterar `feature.task` |
| **Medida** | Nenhuma alteração em `Task`/`TaskService`; apenas um pacote `feature/<novo>` e uma rota |

- **Tática:** manter a coesão semântica; `Task` como aggregate root estável.
- **Mecanismo:** layout `feature/<nome>` + `feature.moduleconfig` (habilita/desabilita módulos por tarefa).
- **Precedente:** `feature.export` foi adicionada exatamente assim.

### QA-06 — Segurança: isolamento de dados entre usuários

| Parte | Valor |
|---|---|
| **Fonte** | Usuário autenticado |
| **Estímulo** | Requisição a um recurso pertencente a outro usuário |
| **Ambiente** | Produção, operação normal |
| **Artefato** | Todos os endpoints de negócio |
| **Resposta** | O recurso não é retornado nem alterado |
| **Medida** | 100% das consultas filtram por `userId` vindo do token, nunca de parâmetro da request |

- **Tática:** autorizar cada acesso a partir do principal autenticado.
- **Mecanismo:** o `JwtAuthFilter` põe o UUID do usuário como principal no
  `SecurityContext`; controllers recebem `@AuthenticationPrincipal UUID userId`.
  O `userId` **nunca** trafega no corpo ou na query string.
- **Evidência:** os `*ServiceTest` de cada feature cobrem o caso "recurso de outro usuário".

### QA-07 — Segurança: superfície pública mínima

| Parte | Valor |
|---|---|
| **Fonte** | Atacante externo |
| **Estímulo** | Tentativa de acesso direto a endpoints internos |
| **Ambiente** | Produção (VPS) |
| **Artefato** | nginx + `WebSecurityConfig` de cada serviço |
| **Resposta** | Endpoints internos não são alcançáveis pela internet |
| **Medida** | `/internal/**` e `/me/data` não possuem rota no proxy; TLS obrigatório (redirect 301 de 80→443) |

- **Táticas:** reduzir superfície de ataque + defesa em profundidade.
- **Mecanismos:** `infra/nginx.conf` — roteamento por *allowlist* de prefixo,
  HSTS, CSP, `X-Content-Type-Options`, `X-Frame-Options`, `Referrer-Policy`,
  `server_tokens off`; além do `X-Internal-Token` exigido pelo notification-service.

---

## 3. Restrições

Impostas, não negociáveis. Não são escolha da arquitetura — são o terreno.

| ID | Restrição | Origem | Consequência arquitetural |
|---|---|---|---|
| RSC-01 | Java 21 + Spring Boot 3.4.1 + Gradle multi-módulo | Técnica/acadêmica | Define o modelo de módulo compartilhado (`libs/common` como `java-library`) |
| RSC-02 | **Banco MySQL único compartilhado pelos 4 serviços** | Custo/infra | Contradiz o estilo microsserviços canônico (*database per service*). Aceita conscientemente: o VPS não comporta 4 instâncias de banco |
| RSC-03 | VPS Oracle ARM, 2 vCPU / 12 GB, instância única por serviço | Orçamento (free tier) | Impede escalabilidade horizontal; ver RISCO-01 |
| RSC-04 | Sem broker de mensagens, sem Redis, sem service discovery | Orçamento/complexidade | Integração é HTTP síncrona ponto a ponto; rate limit e caches são in-process |
| RSC-05 | Frontend em repositório separado (`justdoit-frontend`), hospedado no GitHub Pages | Organização do projeto | Exige CORS configurado em todos os serviços e obriga compatibilidade retroativa de contrato (ex.: `/me/note` foi preservado no refactor de `Note`) |
| RSC-06 | LGPD: usuário pode exportar e apagar seus dados | Legal | Origem direta de RF-06 e RF-07 |

---

## 4. Preocupações arquiteturais

Transversais, não pedidas pelo cliente, mas que o arquiteto precisa resolver.

| ID | Preocupação | Como está resolvida |
|---|---|---|
| PA-01 | Validação de token replicada em todos os serviços | `libs/common` (ver QA-04) |
| PA-02 | Tratamento de erro consistente na API | `GlobalExceptionHandler` + `ErrorResponse` no common |
| PA-03 | Autenticação nos testes de slice | Test fixture `AuthTestSupport.authenticatedUser(UUID)` no common |
| PA-04 | Trabalho periódico sem scheduler externo | Jobs `@Scheduled` in-process: `OverdueTaskJob`, `CycleInstanceJob`, `RefreshTokenCleanupJob` |
| PA-05 | Evolução do schema | Hibernate `ddl-auto=update` em produção, `create-drop` nos testes. Ver RISCO-03 |
| PA-06 | Migração de dados legados | Runner idempotente no boot (`UserNoteMigrationRunner`) |
| PA-07 | Padronização de processo | Branch `feature/JD-XX-nome`, tudo via PR, testes `*ServiceTest` (Mockito) + `*ControllerTest` (MockMvc) |

---

## 5. Riscos e drivers *não* atendidos

Registrados explicitamente para não serem confundidos com qualidades do sistema.

| ID | Item | Situação | Impacto |
|---|---|---|---|
| RISCO-01 | **Escalabilidade horizontal** | **Não é driver hoje.** O `RateLimitFilter` guarda estado em memória do processo e o deploy é de instância única | Subir uma segunda réplica multiplica o limite efetivo por N. Mitigação futura: balde compartilhado (Redis) ou limitar no nginx |
| RISCO-02 | **Observabilidade** | Ausente. Não há métricas nem tracing distribuído | Falhas best-effort viram apenas `log.warn` — não se sabe quantas notificações se perdem nem qual a taxa de erro das integrações |
| RISCO-03 | **Evolução de schema** | `ddl-auto=update` sem Flyway/Liquibase | Não há rollback de migração nem histórico versionado; renomear coluna exige intervenção manual. Tabela `user_note` segue como backup órfão |
| RISCO-04 | **Isolamento de dados entre serviços** | Banco compartilhado (RSC-02) | Nada impede tecnicamente que um serviço leia a tabela de outro. Hoje garantido só por disciplina — ver seção 6 |
| RISCO-05 | **Entrega garantida de notificação** | Não existe por decisão (QA-02) | Aceito. Se virar requisito, exige fila e outbox pattern |
| RISCO-06 | **Ponto único de falha na infraestrutura** | VPS único, nginx único, MySQL único | Indisponibilidade total em caso de queda do host |

---

## 6. Como um driver vira teste executável

Atributos de qualidade **mensuráveis** já têm teste (`RateLimitFilterTest` é a
implementação literal da medida de QA-01).

Drivers de **estrutura** (QA-04, QA-05, RISCO-04) hoje dependem de disciplina.
A forma de torná-los executáveis é um teste de arquitetura — proposta para o
próximo ciclo (ArchUnit ainda **não** está no build):

```java
// Garante RSC-02/RISCO-04: nenhum serviço acopla diretamente em outro serviço.
@Test
void nenhumServicoDependeDeOutroServico() {
    noClasses().that().resideInAPackage("com.justdoit.task..")
        .should().dependOnClassesThat()
        .resideInAnyPackage("com.justdoit.auth..", "com.justdoit.schedule..", "com.justdoit.notification..")
        .check(new ClassFileImporter().importPackages("com.justdoit"));
}

// Garante QA-05: uma feature não enxerga o interior de outra feature.
@Test
void featuresNaoSeAcoplam() {
    slices().matching("com.justdoit.task.feature.(*)..")
        .should().notDependOnEachOther()
        .ignoreDependency(alwaysTrue(), resideInAPackage("com.justdoit.task.feature.task.."))
        .check(new ClassFileImporter().importPackages("com.justdoit.task"));
}
```

---

## 7. Rastreabilidade driver → código

| Driver | Mecanismo | Evidência |
|---|---|---|
| QA-01 | `auth-service/config/RateLimitFilter.java` | `RateLimitFilterTest.java` |
| QA-02 | `task-service/integration/NotificationClient.java`, `schedule-service/integration/TaskReportClient.java` | — (lacuna de teste) |
| QA-03 | `task-service/integration/TaskCompletedListener.java` | — (lacuna de teste) |
| QA-04 | `libs/common/security/*` | `JwtValidatorTest.java` |
| QA-05 | `task-service/feature/**`, `feature/moduleconfig` | `TaskModuleConfigServiceTest.java` |
| QA-06 | `libs/common/security/JwtAuthFilter.java` + `@AuthenticationPrincipal` | `*ServiceTest` de cada feature |
| QA-07 | `infra/nginx.conf`, `WebSecurityConfig` por serviço | — (validação manual) |
| RF-04 | `schedule-service/integration/TaskReportClient.java` | — |
| RF-06 | `task-service/feature/userdata`, `auth-service/feature/auth/TaskServiceClient.java` | — |
| RF-07 | `task-service/feature/export` | `feature/export/*Test.java` |

> As três lacunas de teste acima (QA-02, QA-03, QA-07) são dívida conhecida:
> o driver está implementado, mas nada impede uma regressão silenciosa.

---

## Referências internas

- `README.md` — visão geral e estrutura
- `docs/PLANO-REFACTOR-ARQUITETURA.md` — decisões e motivação do módulo `libs/common`
- `docs/AUDITORIA-JWT-AUTH-SERVICE.md` — auditoria de segurança do fluxo de token
- `docs/INFRA-DEPLOY.md` — topologia de produção
- `docs/TESTES.md` — estratégia de testes
