// Inconclusivos do axe só podem ser aceitos temporariamente quando cada entrada
// possui uma justificativa revisável e uma validade. Entradas expiradas, ausentes
// ou que deixaram de ocorrer reprovam o gate.
export const accessibilityIncompleteAllowlist = [
  {
    surface: 'home',
    rule: 'color-contrast',
    justification: 'O axe não resolve o fundo do anel Pomodoro por causa do pseudo-elemento; revisão manual temporária exigida.',
    reviewedAt: '2026-08-20',
    expiresAt: '2026-09-30',
  },
  {
    surface: 'cadastro',
    rule: 'color-contrast',
    justification: 'O axe não determina fundos em gradiente e elementos parcialmente sobrepostos da tela; revisão manual temporária exigida.',
    reviewedAt: '2026-08-20',
    expiresAt: '2026-09-30',
  },
  {
    surface: 'tarefas',
    rule: 'color-contrast',
    justification: 'O axe não determina o fundo de controles com pseudo-elemento e do estado vazio parcialmente sobreposto; revisão manual temporária exigida.',
    reviewedAt: '2026-08-20',
    expiresAt: '2026-09-30',
  },
  {
    surface: 'anotacoes',
    rule: 'color-contrast',
    justification: 'O axe não determina o fundo do controle com pseudo-elemento; revisão manual temporária exigida.',
    reviewedAt: '2026-08-20',
    expiresAt: '2026-09-30',
  },
  {
    surface: 'configuracoes',
    rule: 'color-contrast',
    justification: 'O axe não determina o fundo dos títulos parcialmente sobrepostos no layout; revisão manual temporária exigida.',
    reviewedAt: '2026-08-20',
    expiresAt: '2026-09-30',
  },
];
