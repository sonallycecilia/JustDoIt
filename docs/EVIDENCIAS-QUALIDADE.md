# Evidências de Qualidade — JustDoIt

> Medições reais executadas em **28/07/2026**, na branch `main`.
> Todos os números deste documento são reprodutíveis pelos comandos indicados.

Este documento reúne três coisas: a **prova de que os testes existem e passam**,
as **métricas de qualidade** medidas por ferramenta, e a **análise de riscos**
com o estado de cada mitigação.

---

## 1. Prova de execução dos testes

### Como reproduzir

```bash
./gradlew test
```

### Resultado da última execução

| Indicador | Valor |
|---|---|
| **Testes executados** | **305** |
| **Falhas** | **0** |
| **Ignorados/pulados** | **0** |
| Classes de teste | 42 |
| Build | `BUILD SUCCESSFUL` |
| Tempo de execução | ~51 s |

### Distribuição por módulo

| Módulo | Classes de teste | Testes |
|---|---:|---:|
| `services/task-service` | 30 | 199 |
| `services/auth-service` | 5 | 40 |
| `services/notification-service` | 3 | 19 |
| `services/schedule-service` | 2 | 19 |
| `libs/common` | 2 | 28 |
| **Total** | **42** | **305** |

### Estratégia de teste (tipos empregados)

O projeto usa quatro níveis de teste, o que caracteriza uma **pirâmide de testes**:

| Nível | Técnica | Quantidade | O que valida |
|---|---|---:|---|
| Unitário | Mockito puro (`@ExtendWith(MockitoExtension.class)`) | 16 classes | Regra de negócio isolada, sem Spring nem banco |
| Unitário puro | JUnit sem Spring nem mocks | 3 classes | Regras autocontidas (validador de texto, JWT, rate limit) |
| Integração de camada (*slice*) | `@WebMvcTest` + MockMvc | 15 classes | Contrato HTTP: rotas, status, serialização, autenticação |
| Integração ponta a ponta | `@SpringBootTest` | 7 classes | Fluxo real com banco H2 em memória |
| Concorrência | `@SpringBootTest` + `ExecutorService` | 1 classe | Regra que só falha sob requisições simultâneas (métrica 3) |

O último nível é novo e vale o destaque: um teste sequencial **não consegue** detectar a
falha que a métrica 3 mede. A brecha só existe quando duas requisições disputam o mesmo
registro no mesmo instante.

### Artefatos de evidência gerados

Relatórios HTML produzidos automaticamente pelo Gradle (abrir no navegador):

```
libs/common/build/reports/tests/test/index.html
services/auth-service/build/reports/tests/test/index.html
services/task-service/build/reports/tests/test/index.html
services/schedule-service/build/reports/tests/test/index.html
services/notification-service/build/reports/tests/test/index.html
```

Os resultados em XML (formato JUnit, consumível por CI) ficam em
`<módulo>/build/test-results/test/*.xml`.

---

## 2. Métricas de qualidade — cobertura de código

A cobertura é medida com **JaCoCo**, configurado em `build.gradle.kts` (raiz) e
aplicado a todos os módulos. O relatório é gerado automaticamente ao final de
`./gradlew test`, sem tarefa extra.

### Como reproduzir

```bash
./gradlew test
# relatório: <módulo>/build/reports/jacoco/test/html/index.html
```

### Resultado consolidado do projeto

| Métrica | Coberto / Total | Percentual |
|---|---:|---:|
| **Linhas** | 1017 / 1375 | **74,0 %** |
| **Instruções** | 5244 / 6953 | **75,4 %** |
| **Métodos** | 292 / 389 | **75,1 %** |
| **Classes** | 101 / 112 | **90,2 %** |
| **Ramos (branches)** | 197 / 338 | **58,3 %** |

> **Como interpretar:** *linhas* é o indicador usual de cobertura. *Ramos* é mais
> exigente — mede se cada `if`/`else` foi testado nos dois caminhos. É normal e
> esperado que ramos fique abaixo de linhas.

### Resultado por módulo

| Módulo | Linhas | Ramos |
|---|---:|---:|
| `services/task-service` | **83,6 %** | 75,0 % |
| `services/notification-service` | 72,0 % | 50,0 % |
| `services/auth-service` | 60,9 % | 26,5 % |
| `services/schedule-service` | 53,1 % | 57,1 % |
| `libs/common` | 52,7 % | 33,3 % |

### Leitura crítica das métricas

Três observações honestas sobre os números acima:

**1. O task-service, que concentra 68 % do código de negócio, tem a melhor
cobertura (83,6 %).** É o resultado desejado: o módulo mais complexo é o mais
testado.

**2. O `libs/common` tem a pior cobertura (52,7 %) e é o módulo mais crítico.**
Detalhamento por classe:

| Classe | Cobertura | Observação |
|---|---:|---|
| `TextoSeguroValidator` | **100,0 %** (17/17 linhas) | Testado por `TextoSeguroValidatorTest` |
| `JwtValidator` | 63,2 % (12/19 linhas) | Testado por `JwtValidatorTest` |
| `JwtAuthFilter` | **0,0 %** (0/13 linhas) | **Sem nenhum teste direto** |
| `GlobalExceptionHandler` | 0,0 % (0/5 linhas) | Sem teste direto |
| `ErrorResponse` | 0,0 % (0/1 linhas) | Record simples, risco desprezível |

O `JwtAuthFilter` é a classe que **autentica todas as requisições dos 4
serviços**. Ela funciona — é exercitada indiretamente pelos `@SpringBootTest` —
mas não possui teste próprio que documente seu comportamento. Ver RISCO-07.

**3. Ramos em 26,5 % no auth-service** indica caminhos condicionais de
autenticação (token expirado, assinatura inválida, refresh em janela de graça)
não exercitados nos dois sentidos.

---

## 3. Análise e mitigação de riscos

Riscos classificados por **probabilidade** × **impacto**, com o estado real da
mitigação. Nenhum item está marcado como resolvido sem evidência.

### Matriz de risco

| ID | Risco | Prob. | Impacto | Severidade | Estado |
|---|---|---|---|---|---|
| R-01 | Ataque de força bruta / credential stuffing no login | Alta | Alto | **Crítico** | ✅ Mitigado |
| R-02 | Vazamento de dados entre usuários | Baixa | Crítico | **Alto** | ✅ Mitigado |
| R-03 | Queda de um serviço derruba os demais | Média | Alto | **Alto** | ✅ Mitigado |
| R-04 | Notificação falsa após rollback de transação | Média | Médio | **Médio** | ✅ Mitigado |
| R-05 | Perda de dados na evolução do schema | Média | Crítico | **Alto** | ⚠️ Aceito |
| R-06 | Indisponibilidade total (ponto único de falha) | Média | Alto | **Alto** | ⚠️ Aceito |
| R-07 | Regressão silenciosa no filtro de autenticação | Média | Crítico | **Alto** | ❌ Não mitigado |
| R-08 | Ausência de observabilidade em produção | Alta | Médio | **Médio** | ❌ Não mitigado |
| R-09 | Rate limit ineficaz ao escalar horizontalmente | Baixa | Alto | **Médio** | ⚠️ Aceito |
| R-10 | Perda silenciosa de notificações | Alta | Baixo | **Baixo** | ⚠️ Aceito |
| R-11 | Tempo de tarefa corrompido por concorrência no cronômetro | Alta | Médio | **Médio** | ✅ Mitigado |

### Detalhamento e mitigação

#### R-01 — Força bruta no login · ✅ Mitigado
- **Cenário:** robô testa senhas em massa contra `/auth/login`, ou enumera e-mails via `/auth/check-email`.
- **Mitigação:** `RateLimitFilter` — *token bucket* por IP, 20 req/min, resposta HTTP 429 + `Retry-After`.
- **Evidência:** `RateLimitFilterTest` — 5 testes cobrindo o estouro do limite, o isolamento por IP, o comportamento atrás de proxy (`X-Forwarded-For`), as rotas não limitadas e o *kill switch*.
- **Risco residual:** ver R-09.

#### R-02 — Vazamento de dados entre usuários · ✅ Mitigado
- **Cenário:** usuário A acessa tarefa do usuário B manipulando o ID na URL.
- **Mitigação estrutural:** os repositórios **não expõem** consulta sem dono. Só existem `findByIdAndUserId`, `findByUserId`, `findByCategoryIdAndUserId` — o `userId` é parâmetro obrigatório. E ele vem sempre do token (`@AuthenticationPrincipal`), nunca da requisição.
- **Por que é forte:** não depende de disciplina do programador. Escrever a consulta insegura exigiria criar um método novo no repositório — o compilador barra o caminho fácil.
- **Evidência:** os 8 `*ServiceTest` do task-service exercitam `findByIdAndUserId`.

#### R-03 — Efeito dominó entre serviços · ✅ Mitigado
- **Cenário:** notification-service cai; usuário não consegue mais concluir tarefas.
- **Mitigação:** `NotificationClient` e `TaskReportClient` com timeouts curtos (2 s conexão / 3 s leitura) e falha engolida — a operação de negócio conclui normalmente.
- **Risco residual:** R-10.
- **Lacuna:** não há teste automatizado provando o comportamento. Deveria haver.

#### R-04 — Notificação falsa após rollback · ✅ Mitigado
- **Cenário:** tarefa é marcada como concluída, a transação falha no commit, mas o usuário já recebeu "tarefa concluída".
- **Mitigação:** `TaskCompletedListener` com `@TransactionalEventListener(phase = AFTER_COMMIT)` — o envio só ocorre após o commit confirmado.
- **Lacuna:** sem teste automatizado provando o comportamento em rollback.

#### R-05 — Perda de dados na evolução do schema · ⚠️ Aceito
- **Cenário:** `ddl-auto=update` não versiona nem reverte migração. Renomear coluna implica perda de dados.
- **Por que foi aceito:** projeto acadêmico, sem dados de produção reais; adicionar Flyway custaria mais do que o risco justifica no momento.
- **Mitigação parcial:** migrações de dados são feitas por *runner* idempotente no boot (`UserNoteMigrationRunner`), e a tabela antiga `user_note` foi **preservada como backup** em vez de removida.
- **Plano:** adotar Flyway antes de qualquer uso com dados reais.

#### R-06 — Ponto único de falha · ⚠️ Aceito
- **Cenário:** VPS único; se o host cai, todo o sistema sai do ar.
- **Por que foi aceito:** restrição de orçamento (Oracle free tier). Redundância exigiria segundo host.
- **Mitigação parcial:** serviços registrados como *systemd units* (`infra/justdoit-*.service`) — reiniciam sozinhos após falha de processo ou reboot.

#### R-07 — Regressão no filtro de autenticação · ❌ Não mitigado
- **Cenário:** alteração no `JwtAuthFilter` quebra a autenticação ou, pior, passa a aceitar token inválido — em **todos os 4 serviços de uma vez**.
- **Estado:** a classe tem **0 % de cobertura**. Não há teste que detecte a regressão.
- **Agravante:** foi centralizada em `libs/common` justamente para ter um ponto único de manutenção — o que também a torna um ponto único de falha.
- **Mitigação proposta:** teste unitário do filtro cobrindo: header ausente, header malformado, token inválido, token válido (principal correto no `SecurityContext`) e o `shouldNotFilterErrorDispatch`. Estimativa: ~5 testes.

#### R-08 — Ausência de observabilidade · ❌ Não mitigado
- **Cenário:** falhas best-effort (R-03, R-10) só produzem `log.warn`. Não se sabe quantas notificações se perdem, nem se o rate limit está barrando usuário legítimo.
- **Mitigação proposta:** Spring Boot Actuator + Micrometer, expondo contadores de falha de integração e de rejeições 429.

#### R-09 — Rate limit ao escalar · ⚠️ Aceito
- **Cenário:** com N réplicas, cada uma mantém seu próprio balde — o limite efetivo vira 20 × N.
- **Por que foi aceito:** o deploy é de instância única (R-06); o risco só se materializa se houver escala horizontal, que a infraestrutura atual não comporta.
- **Mitigação registrada no próprio código** (`RateLimitFilter`, comentário de classe): migrar para balde compartilhado (Redis) ou aplicar o limite no nginx.

#### R-10 — Perda de notificação · ⚠️ Aceito
- **Cenário:** notification-service indisponível no instante do envio → notificação perdida, sem retry.
- **Por que foi aceito:** é a contrapartida deliberada de R-03. Notificação é informativa, não transacional.
- **Mitigação futura, se virar requisito:** *outbox pattern* com fila.

#### R-11 — Tempo corrompido por concorrência no cronômetro · ✅ Mitigado
- **Cenário:** o usuário aciona o cronômetro em duas tarefas (duas abas, duplo clique) e as
  duas acumulam tempo em paralelo; ou dois logs simultâneos leem o mesmo `actual_seconds`,
  somam e o último salva — perdendo o tempo do outro em silêncio.
- **Por que era provável:** o backend não tinha noção de "cronômetro ativo" (só um acumulador),
  e a soma era leitura-modificação-escrita sem lock.
- **Mitigação:** índice **único em `active_timer.user_id`** — um cronômetro ativo por usuário,
  garantido pelo banco e não por verificação de aplicação; e soma por **UPDATE atômico**
  (`TaskTimerRepository.incrementActualSeconds`).
- **Por que é forte:** vale entre instâncias do serviço. Diferente do rate limit (R-09), não
  perde eficácia com réplicas.
- **Evidência:** `CronometroConcorrenteMetricsTest` — métrica de desempenho em **1,0000**
  (130/130 acionamentos simultâneos bloqueados), mais a verificação no banco de que só a
  tarefa vencedora acumulou tempo e o teste de 10 logs simultâneos somando exatamente 10 s.
- **Risco residual:** cronômetro esquecido aberto por dias ainda soma tempo irreal no `stop`
  — o gancho `BiologicalCeilingProperties` existe no código, mas segue órfão.

### Resumo quantitativo

| Estado | Quantidade |
|---|---:|
| ✅ Mitigado com evidência | 5 |
| ⚠️ Aceito conscientemente (com justificativa e plano) | 4 |
| ❌ Não mitigado (com proposta definida) | 2 |

---

## 4. Ações prioritárias recomendadas

Em ordem de custo-benefício:

| # | Ação | Resolve | Esforço |
|---|---|---|---|
| 1 | Testar `JwtAuthFilter` (~5 testes) | R-07, e sobe `libs/common` de 52,7 % para ~76 % | Baixo |
| 2 | Testar o comportamento best-effort dos clientes HTTP | Lacuna de R-03 | Baixo |
| 3 | Testar `TaskCompletedListener` em rollback | Lacuna de R-04 | Médio |
| 4 | Elevar cobertura de ramos do auth-service (26,5 %) | Caminhos de falha de token | Médio |
| 5 | Adicionar Spring Boot Actuator | R-08 | Médio |
| 6 | Adotar Flyway | R-05 | Alto |

---

## Documentos relacionados

- `DRIVERS-ARQUITETURAIS.md` — drivers, atributos de qualidade e decisões
- `docs/TESTES.md` — estratégia e convenções de teste
- `docs/AUDITORIA-JWT-AUTH-SERVICE.md` — auditoria de segurança do fluxo de token
