# Análise e mitigação de riscos

## Identificação

| Campo | Valor |
|---|---|
| Sistema | JustDoIt — Gerenciador de Tarefas |
| Componente | Cronômetro |
| Cenário | Cliques repetidos antes da atualização visual |
| Método | Matriz qualitativa `Probabilidade × Severidade` |

## Critérios da métrica

### Probabilidade (P)

| Nota | Critério |
|---:|---|
| 1 | Rara — exige uma condição excepcional |
| 2 | Improvável — pode ocorrer ocasionalmente |
| 3 | Possível — cenário reproduzível em uso normal |
| 4 | Provável — ocorre com frequência |
| 5 | Quase certa — ocorre de forma recorrente |

### Severidade (S)

| Nota | Critério |
|---:|---|
| 1 | Insignificante — sem impacto funcional relevante |
| 2 | Baixa — impacto pequeno e facilmente reversível |
| 3 | Moderada — inconsistência funcional ou retrabalho do usuário |
| 4 | Alta — perda de dados, indisponibilidade parcial ou impacto amplo |
| 5 | Crítica — indisponibilidade grave, violação de segurança ou perda irreversível |

### Classificação

O índice de risco é calculado por `R = P × S`.

| Resultado | Classificação | Tratamento |
|---:|---|---|
| 1–4 | Baixo | Aceitar e monitorar |
| 5–9 | Moderado | Planejar e implementar mitigação |
| 10–16 | Alto | Mitigação prioritária |
| 17–25 | Crítico | Interromper a exposição e tratar imediatamente |

## Registro do risco

| Campo | Avaliação |
|---|---|
| Palavra-guia | Mais |
| Defeito | Cliques repetidos antes da atualização visual iniciam múltiplas operações concorrentes do cronômetro. |
| Causa | Ausência de bloqueio síncrono do comando, controle de estado e idempotência. O atraso de renderização mantém o controle aparentemente disponível e favorece novos disparos. |
| Consequência | Estado inconsistente, múltiplos cronômetros ou requisições, registros duplicados, mensagens repetidas e perda de confiabilidade dos tempos. |
| Detecção | Teste automatizado com cliques rápidos, verificando a quantidade de eventos, requisições, operações persistidas e instâncias ativas do cronômetro. |
| Probabilidade inerente | 3 — Possível |
| Severidade inerente | 3 — Moderada |
| Risco inerente | **9 — Moderado** |

## Plano de mitigação

1. Bloquear o comando imediatamente no início do manipulador, antes de qualquer operação assíncrona.
2. Manter um estado explícito de operação em andamento e ignorar novos disparos até a conclusão ou falha controlada.
3. Garantir idempotência no backend para que comandos repetidos com a mesma chave não criem operações duplicadas.
4. Impedir, por regra de negócio, mais de um cronômetro ativo para o mesmo usuário e tarefa.
5. Aplicar `debounce` apenas como proteção complementar; ele não substitui controle de estado nem idempotência.
6. Otimizar a renderização e o retorno visual do controle para reduzir a janela de repetição do clique.
7. Registrar telemetria para comandos descartados, conflitos e tentativas de criação duplicada.

## Validação e risco residual esperado

| Campo | Valor |
|---|---|
| Critério de aceitação | Mesmo com vários cliques em sequência, somente uma operação é processada, apenas um cronômetro permanece ativo e o estado final fica consistente. |
| Teste mínimo | Executar rajadas de cliques antes, durante e após a resposta do backend; repetir com latência artificial e falhas de rede. |
| Probabilidade residual esperada | 1 — Rara |
| Severidade residual | 3 — Moderada |
| Risco residual esperado | **3 — Baixo** |
| Condição para aceite | Testes automatizados aprovados e evidência de que a regra também é garantida pelo backend. |
| Responsável | A definir |
| Prazo | A definir |
| Estado | Proposto |

> O risco residual somente deve ser confirmado após a implementação e a execução dos testes. Até lá, o valor 3 é uma meta de tratamento, não uma medição concluída.

## Evidência original

A captura que originou esta revisão está em [img_1.png](img_1.png).
