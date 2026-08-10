# Mitigação de risco — responsividade e autosave

## Identificação

| Campo | Valor |
|---|---|
| Sistema | JustDoIt — Gerenciador de Tarefas |
| Parâmetro | Usabilidade, prevenção de erros e autosave |
| Palavra-guia | Depois |
| Risco inerente | 8 — Probabilidade 4 × Severidade 2 |

## Risco analisado

O acesso síncrono ao `localStorage` durante a montagem do formulário poderia atrasar a primeira renderização e produzir sensação de lentidão, quebra do estado de concentração e cliques repetidos. A restauração tardia também poderia sobrescrever texto digitado enquanto o rascunho ainda estivesse sendo carregado.

## Mitigação implementada

- A leitura do rascunho foi retirada do caminho crítico da primeira renderização.
- A operação é agendada com `requestIdleCallback`, com limite de espera de 250 ms.
- Navegadores sem `requestIdleCallback` utilizam `setTimeout` como fallback assíncrono.
- As gravações mantêm debounce de 500 ms e são executadas quando o navegador está ocioso.
- Ao desmontar a página, dados pendentes são gravados imediatamente para evitar perda do rascunho.
- Texto digitado enquanto a restauração está pendente tem prioridade sobre o conteúdo antigo.
- O autosave controlado só começa depois que a leitura inicial termina.

## Implementação

| Arquivo | Responsabilidade |
|---|---|
| `src/features/tasks/hooks/useRascunhoTarefa.js` | Agendamento ocioso, leitura assíncrona, debounce, cancelamento e gravação no desmonte |
| `src/features/tasks/components/TaskEditor.jsx` | Restauração assíncrona e proteção contra sobrescrita de texto novo |
| `src/features/tasks/hooks/useRascunhoTarefa.test.jsx` | Testes da persistência, restauração, adiamento da leitura e concorrência com digitação |

Os arquivos de implementação pertencem ao repositório `justdoit-frontend`, branch `qualidade`, commit `53a1678`.

## Evidências de validação

- Suíte completa do frontend: 16 arquivos de teste aprovados e 81 testes aprovados.
- Testes diretamente relacionados: 10 testes aprovados após o ajuste de concorrência.
- Build Vite de produção concluído com sucesso.
- Verificação `git diff --check` concluída sem erros.

## Critérios de aceitação

- A primeira renderização não acessa o rascunho no `localStorage`.
- O rascunho continua sendo restaurado ao retornar à criação de tarefa.
- A saída da página antes do debounce não perde dados.
- O registro bem-sucedido da tarefa elimina o rascunho.
- Conteúdo recém-digitado não é substituído por um rascunho antigo.

## Risco residual esperado

| Campo | Valor |
|---|---|
| Probabilidade residual | 1 — Rara |
| Severidade residual | 2 — Baixa |
| Risco residual | 2 — Baixo |

O risco residual deve ser acompanhado por métricas reais de interação, como INP, tarefas longas e tempo entre clique e atualização visual.

## Evidência original

A captura que originou esta mitigação está em [img.png](img.png).
