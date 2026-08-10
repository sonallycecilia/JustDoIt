# Instruções para o Cowork — atualização das planilhas de métricas e mitigação

## Objetivo

Atualizar as planilhas originais de análise/métrica e mitigação de riscos do JustDoIt, adicionando comentários que registrem o que foi efetivamente implementado e validado.

As capturas de referência estão em:

- `docs/qualidade/img.png` — risco de responsividade, prevenção de erros e autosave;
- `docs/qualidade/img_1.png` — risco de sincronismo e concorrência do cronômetro.

> As imagens são somente referências visuais. Não editar ou recriar uma planilha a partir do PNG se o arquivo original `.xlsx` estiver disponível. Solicitar/anexar a planilha original antes da edição.

## Repositórios e pastas da implementação

| Escopo | Repositório local | Branch | Pasta principal |
|---|---|---|---|
| Backend e documentação | `C:\Users\sonal\Documents\vsProjects\JustDoIt` | `qualidade` | `services/task-service` e `docs/qualidade` |
| Frontend | `C:\Users\sonal\Documents\vsProjects\justdoit-frontend` | `qualidade` | `src/features/tasks/qualidade` |

Os caminhos apresentados nas seções seguintes são relativos ao respectivo repositório indicado nesta tabela.

## Regras para a edição

1. Preservar layout, fórmulas, cores, bordas, mesclagens e escalas existentes.
2. Adicionar comentários nas células das linhas correspondentes, preferencialmente na célula **Providências** ou **Risco**. Não substituir o texto original da análise.
3. Identificar o comentário como atualização de implementação e incluir a data da revisão.
4. Não declarar risco residual como confirmado quando depender de medição em produção.
5. Se houver colunas para responsável, prazo, status ou risco residual, preenchê-las conforme as orientações abaixo. Se não existirem, manter essas informações no comentário.
6. Renderizar a planilha após a alteração e conferir se nenhum texto, comentário, célula ou fórmula ficou cortado ou corrompido.

## Linha 1 — Responsividade, prevenção de erros e autosave (`img.png`)

Localizar a linha com:

- palavra-guia: **Depois**;
- risco atual: `P = 4`, `S = 2`, `Risco = 8`;
- providência relacionada à otimização da renderização assíncrona e armazenamento local.

Implementação localizada no repositório frontend `C:\Users\sonal\Documents\vsProjects\justdoit-frontend`, branch `qualidade`, principalmente na pasta `src/features/tasks/qualidade`.

### Comentário a inserir

> **Atualização da implementação — 10/08/2026:** a mitigação foi implementada no frontend, branch `qualidade`. A leitura do rascunho foi retirada do caminho crítico da primeira renderização e passou a usar `requestIdleCallback`, com fallback assíncrono e limite de espera de 250 ms. As gravações mantêm debounce e execução ociosa; no desmonte, dados pendentes são gravados imediatamente. A restauração não sobrescreve texto digitado enquanto a leitura estava pendente. O código de mitigação e o monitoramento ficaram separados em `src/features/tasks/qualidade`. Foram adicionadas medições de montagem do formulário, Event Timing/INP, tarefas longas e cálculo P75, além de teste com rascunho de 1 MB. Validação: 17 arquivos de teste e 86 testes aprovados; build Vite aprovado. Commits principais do frontend: `04bae94` e `6123806`. O risco residual esperado é `P = 1`, `S = 2`, `R = 2`, mas só deve ser confirmado após coleta de métricas reais em produção.

### Evidências

- `src/features/tasks/qualidade/responsividadeAutosave.js`
- `src/features/tasks/qualidade/metricasResponsividadeAutosave.js`
- `src/features/tasks/qualidade/ResponsividadeAutosaveMetrics.test.jsx`
- `src/features/tasks/qualidade/MetricasResponsividadeAutosave.test.jsx`
- `src/features/tasks/hooks/useRascunhoTarefa.js`
- `src/features/tasks/components/TaskEditor.jsx`
- `docs/qualidade/mitigacao-responsividade-autosave.md`

### Campos sugeridos

| Campo | Valor |
|---|---|
| Status | Implementado; monitoramento em produção pendente |
| Responsável | Equipe frontend |
| Probabilidade residual esperada | 1 |
| Severidade residual | 2 |
| Risco residual esperado | 2 — Baixo |

## Linha 2 — Sincronismo e concorrência do cronômetro (`img_1.png`)

Localizar a linha com:

- palavra-guia: **Mais**;
- risco atual: `P = 3`, `S = 3`, `Risco = 9`;
- defeito relacionado a cliques repetidos e múltiplos disparos do cronômetro.

Implementação localizada no repositório backend `C:\Users\sonal\Documents\vsProjects\JustDoIt`, branch `qualidade`, no módulo `services/task-service`.

### Comentário a inserir

> **Atualização da implementação — 10/08/2026:** existe proteção no backend para impedir mais de um cronômetro ativo por usuário. A tabela `active_timer` possui restrição única em `user_id`; o serviço usa `saveAndFlush` e converte disputa concorrente em HTTP 409. Há uma suíte de qualidade que dispara requisições simultâneas, verifica a taxa de bloqueio esperada (`X = bloqueadas/concorrentes = 1,0`), confirma apenas um cronômetro ativo e impede acúmulo paralelo em tarefas perdedoras. Evidências: `ActiveTimer.java`, `TaskTimerService.java`, `V1__task_baseline.sql` e `CronometroConcorrenteMetricsTest.java`. Não registrar o risco residual como confirmado sem executar a suíte no ambiente da revisão e verificar também disparos concorrentes de parada (`stop`) e idempotência ponta a ponta.

### Evidências

- `services/task-service/src/main/java/com/justdoit/task/feature/timer/ActiveTimer.java`
- `services/task-service/src/main/java/com/justdoit/task/feature/timer/TaskTimerService.java`
- `services/task-service/src/main/resources/db/migration/V1__task_baseline.sql`
- `services/task-service/src/test/java/com/justdoit/task/qualidade/CronometroConcorrenteMetricsTest.java`
- `docs/qualidade/analise-mitigacao-riscos.md`

### Campos sugeridos

| Campo | Valor |
|---|---|
| Status | Parcialmente validado; executar suíte e revisar concorrência do `stop` |
| Responsável | Equipe task-service e frontend |
| Risco residual | Não confirmar até concluir a validação pendente |

## Verificações obrigatórias antes de entregar

- Confirmar que os comentários foram inseridos nas duas linhas corretas.
- Conferir fórmulas `P × S` e não transformar valores calculados em texto.
- Não alterar os valores inerentes `8` e `9`; valores residuais devem ficar em campos separados ou somente nos comentários.
- Pesquisar erros de fórmula como `#REF!`, `#VALUE!`, `#DIV/0!` e `#NAME?`.
- Renderizar todas as abas alteradas e verificar visualmente o resultado.
- Entregar o `.xlsx` atualizado, mantendo o nome original com o sufixo `-atualizado`, salvo se o usuário solicitar substituição.

## Resultado esperado

Uma planilha atualizada e auditável, na qual cada risco mantém sua análise original e recebe um comentário rastreável para código, testes, branch e commits, distinguindo claramente entre:

- mitigação implementada;
- teste automatizado aprovado;
- medição de produção ainda pendente;
- risco residual esperado versus risco residual confirmado.
