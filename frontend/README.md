# JustDoIt Frontend

Interface web do JustDoIt, uma aplicação de produtividade pessoal para tarefas,
agenda, tempo, notas, ciclos semanais e análises.

> Estado verificado em 20/08/2026 contra `origin/main`, commit `540508f`.
> Informações externas de deploy e qualidade também foram conferidas no GitHub.

## Visão rápida

O frontend é uma SPA em JavaScript e JSX, construída com React 18 e Vite 6. A
aplicação não usa TypeScript nem uma store global. Dados do servidor ficam no
TanStack Query; estado visual local usa hooks do React.

Principais dependências de runtime:

- React, React DOM e React Router;
- TanStack Query para cache e sincronização da API;
- TipTap para o editor de texto rico das anotações;
- `react-window` para virtualizar listas grandes de tarefas;
- `@marsidev/react-turnstile` para o desafio anti-bot.

## Estrutura

```text
justdoit-frontend/
├── docs/                 documentação
├── public/               CNAME e arquivos copiados para o build
├── scripts/              coleta e geração das métricas de qualidade
├── src/
│   ├── api/              cliente HTTP, endpoints e sessão
│   ├── components/       componentes compartilhados
│   ├── features/         código organizado por funcionalidade
│   ├── hooks/            hooks compartilhados
│   ├── lib/              utilitários
│   └── styles/           design system em CSS puro
└── tests/                auditorias Playwright de acessibilidade e responsividade
```

Cada feature cria apenas as pastas de que precisa (`pages`, `components`,
`hooks`, `api` ou `lib`). Imports entre áreas usam o alias `@/`.

## Telas e funcionalidades

| Área | Rotas | Estado implementado |
|---|---|---|
| Landing e autenticação | `/`, `/signup`, `/onboarding` | login e cadastro com Turnstile, perfil inicial |
| Visão geral | `/visao-geral` | resumo de tarefas e produtividade |
| Tarefas | `/todo`, `/tasks/nova`, `/tasks/:id` | CRUD, subtarefas, timer, foco, recorrência e lembrete |
| Anotações | `/anotacoes` | notas livres com editor TipTap e nota fixada |
| Calendário | `/calendario` | grade semanal, blocos, arrastar e soltar e painel de evento |
| Análises | `/analise` | indicadores gerais, categorias e semanas fechadas |
| Configurações | `/configuracoes` | conta, tema, categorias, exportação e exclusão |
| Fechamento semanal | `/history`, `/history/:cycleId` | fechamento, histórico e snapshots do ciclo |

Também existem, sem rota própria, a central de notificações e o canal “Falar com
o desenvolvimento”.

## Responsividade atual

- Todas as 13 superfícies auditáveis são verificadas em 320 × 568 e 390 × 844.
- O menu lateral pode ser aberto e redimensionado arrastando toda a borda
  direita no celular. A largura móvel e a largura desktop são persistidas
  separadamente.
- O calendário móvel mantém a grade completa na vertical. A página rola para
  baixo até a legenda, em vez de comprimir ou cortar a grade.
- O painel lateral de detalhes do calendário pode ser redimensionado no desktop.
- Os divisores de redimensionamento aceitam ponteiro e teclado.
- O Turnstile usa o formato compacto até 332 px e o formato flexível nas telas
  maiores, evitando overflow no cadastro.

## API e estado remoto

`src/api/endpoints.js` concentra as URLs. Em desenvolvimento, os serviços usam
`localhost` nas portas 8080–8083. Fora de `localhost`, todos usam
`https://justdoitapi.duckdns.org`.

`src/api/client.js`:

- injeta o access token no header `Authorization`;
- compartilha uma única renovação entre requisições concorrentes;
- tenta novamente a chamada depois de renovar o token;
- encerra a sessão somente quando a credencial foi realmente recusada;
- preserva a sessão em falhas transitórias como rede, 429 e 5xx;
- baixa arquivos sem tentar interpretar CSV como JSON.

O backend continua sendo a fonte de verdade dos dados de negócio. As exceções
locais são rascunhos ainda não enviados e preferências de interface.

## Dados mantidos no navegador

### Sessão

| Storage | Chave | Conteúdo |
|---|---|---|
| `sessionStorage` | `jdi.sessao.ativa` | sessão ativa da aba |
| `localStorage` | `jdi.sessao.lembrada.<id>` | sessões marcadas como “manter conectado” |
| `localStorage` | `jdi.sessao.ultima` | identificador da última sessão persistente |
| ambos | `jdi.sessao` | formato antigo, lido apenas para migração |

### Preferências, rascunhos e estado visual

- `jdi.tema` e `jdi.inicio-semana`;
- `jdi-sidebar-collapsed`, `jdi-sidebar-width` e
  `jdi-sidebar-mobile-width`;
- `jdi-calendar-drawer-width`;
- `jdi.todo-notas` e `jdi.todo-nota-titulo`;
- `jdi.rascunho.tarefa`;
- `jdi_usability_flows`, usado pela instrumentação local de usabilidade;
- `jdi-browser-notifications-shown` no `sessionStorage`, para não repetir
  notificações do navegador na mesma aba.

Access e refresh tokens ainda ficam acessíveis ao JavaScript. O risco e a
migração planejada estão documentados em
[Risco de sessão](docs/security/session-storage-risk.md) e
[SEC-001](docs/backlog/SEC-001-http-only-session.md).

## Executar localmente

Pré-requisitos: Node 20 ou superior e o backend rodando ao lado.

```bash
npm install
npm run dev
```

O Vite escuta `127.0.0.1:3000` com `strictPort`. A porta é fixa porque é a origem
permitida por padrão no CORS do backend.

Comandos úteis:

| Comando | Função |
|---|---|
| `npm run dev` | desenvolvimento com HMR |
| `npm run build` | build de produção em `dist/` |
| `npm run preview` | serve o build localmente |
| `npm test` | suíte Vitest |
| `npm run quality:lcp` | LCP P75 da página inicial |
| `npm run quality:a11y` | axe-core/Playwright nas rotas auditáveis |
| `npm run quality:responsive` | auditoria em 320 px e 390 px |
| `npm run quality:session` | 11 cenários do ciclo de sessão |
| `npm run quality:all` | executa todos os gates e gera relatórios |

`run.py` oferece os comandos `start`, `front` e `back` para orquestração local,
mas depende de o backend existir na pasta irmã esperada.

## Qualidade verificada

Na execução aprovada do commit `1b4663b`, posteriormente integrado à `main`:

- suíte Vitest: aprovada;
- LCP P75: **913 ms**, com amostras 846, 847, 913 e 1092 ms;
- acessibilidade automatizada: aprovada nas 13 rotas auditáveis;
- responsividade: **29/29** cenários aprovados;
- proteção do ciclo de sessão: **11/11 (100%)**.

A execução pode ser consultada no
[GitHub Actions](https://github.com/sonallycecilia/justdoit-frontend/actions/runs/32437634036).
Os documentos em `docs/quality/` são snapshots e informam a execução a que se
referem; não substituem teste manual com leitor de tela nem métricas reais de
jornada em produção.

## Deploy

O GitHub Pages está configurado atualmente com `build_type: workflow`. Um push
na `main` executa `.github/workflows/deploy.yml`, gera `dist/` e publica pelo
GitHub Actions.

- URL: `https://justdoit-app.duckdns.org/`
- `public/CNAME` preserva o domínio próprio.
- O build cria `404.html` como fallback das rotas do React Router.
- `VITE_TURNSTILE_SITE_KEY` é fornecida como secret durante o build.

## Documentação

- [Correção funcional](docs/quality/correcao-funcional.md)
- [Desempenho](docs/quality/desempenho.md)
- [Segurança](docs/quality/seguranca.md)
- [Usabilidade e responsividade](docs/quality/usabilidade.md)
- [Risco de credenciais no Web Storage](docs/security/session-storage-risk.md)
- [Plano SEC-001 para cookies HttpOnly](docs/backlog/SEC-001-http-only-session.md)
