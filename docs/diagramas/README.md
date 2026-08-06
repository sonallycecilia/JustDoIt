# Diagramas do JustDoIt

Diagramas em [Mermaid](https://mermaid.js.org/) do fluxo completo do sistema.
Renderizam direto no GitHub, no VS Code (extensão *Markdown Preview Mermaid*) e
no IntelliJ (plugin *Mermaid*).

Cada arquivo é autocontido: tem o diagrama e a explicação em texto do que ele
mostra, com os pontos que costumam gerar pergunta em apresentação.

## Comece aqui

[**00-fluxo-geral.md**](00-fluxo-geral.md) — **um** diagrama com o fluxo inteiro,
do clique até o banco, sem detalhe nenhum. É o de abrir apresentação; todos os
outros arquivos são aprofundamentos de alguma caixa dele.

## Se a pergunta é "explique o fluxo do código"

Depois do 00, estes três — o mapa das portas de entrada e os dois lados do
caminho por dentro:

| Arquivo | O que responde |
|---|---|
| [13-rotas.md](13-rotas.md) | **Todas as rotas** dos 4 serviços, com método, auth, status e o que cada uma faz |
| [14-fluxo-em-camadas.md](14-fluxo-em-camadas.md) | **O caminho pelas camadas** — Controller → Service → Repository → banco, com nomes reais de classe e método |
| [15-fluxo-frontend.md](15-fluxo-frontend.md) | **A metade do navegador** — do clique ao `fetch`: hook, endpoints, client, sessão e renovação de token |

## Ordem sugerida para apresentar

| # | Arquivo | O que responde |
|---|---|---|
| 0 | [00-fluxo-geral.md](00-fluxo-geral.md) | O fluxo inteiro em um diagrama só |
| 1 | [01-visao-geral.md](01-visao-geral.md) | Quais são as peças e quem fala com quem |
| 2 | [02-autenticacao.md](02-autenticacao.md) | Cadastro, login, rotação de refresh token, logout |
| 3 | [03-request-autenticada.md](03-request-autenticada.md) | O que acontece com uma request do momento que sai do navegador até o banco |
| 4 | [04-modelo-de-dados.md](04-modelo-de-dados.md) | Quais tabelas existem e como se relacionam |
| 5 | [05-ciclo-de-vida-tarefa.md](05-ciclo-de-vida-tarefa.md) | Estados de uma tarefa e o fluxo de conclusão |
| 6 | [06-cronometro.md](06-cronometro.md) | Como o tempo é medido e por que só há um cronômetro por usuário |
| 7 | [07-recorrencia.md](07-recorrencia.md) | Como tarefas cíclicas viram tarefas reais no calendário |
| 8 | [08-resumo-semanal.md](08-resumo-semanal.md) | Como o planejado se compara ao executado, cruzando dois serviços |
| 9 | [09-jobs-agendados.md](09-jobs-agendados.md) | O que o sistema faz sozinho, sem usuário |
| 10 | [10-exclusao-de-conta.md](10-exclusao-de-conta.md) | Exclusão de conta atravessando dois serviços |
| 11 | [11-validacao-de-entrada.md](11-validacao-de-entrada.md) | Como texto malicioso é barrado |
| 12 | [12-topologia-deploy.md](12-topologia-deploy.md) | Onde isso roda em produção |
| 13 | [13-rotas.md](13-rotas.md) | Inventário completo de rotas e a convenção de status HTTP |
| 14 | [14-fluxo-em-camadas.md](14-fluxo-em-camadas.md) | O caminho do código pelas camadas, classe por classe |
| 15 | [15-fluxo-frontend.md](15-fluxo-frontend.md) | O que acontece dentro do navegador antes da request sair |

## Convenções de cor

Os diagramas usam a mesma paleta em todos os arquivos:

- **roxo** — cliente / frontend
- **azul** — serviço backend
- **verde** — persistência e infraestrutura
- **âmbar** — execução automática, sem usuário presente
- **vermelho** — caminho de erro, rejeição ou pendência conhecida
