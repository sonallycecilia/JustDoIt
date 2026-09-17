// Prioridade: níveis da UI ↔ enum Priority do backend.
// A prioridade agora é persistida no backend (campo priority do TaskRequest);
// no front antigo ela vivia só num cache local e se perdia entre dispositivos.
export const NIVEIS = ['urgent', 'important', 'normal', 'low'];

export const ROTULO = {
  urgent: 'Urgente',
  important: 'Importante',
  normal: 'Normal',
  low: 'Baixa',
};

export const COR = {
  urgent: 'var(--color-priority-urgent)',
  important: 'var(--color-priority-important)',
  normal: 'var(--color-priority-normal)',
  low: 'var(--color-priority-low)',
};

// Matriz de Eisenhower do backend ↔ níveis da UI (bijeção: o valor sempre
// sobrevive à ida e volta). "low" usa URGENT_NOT_IMPORTANT, o quadrante
// "delegável" — o de menor prioridade de execução própria.
const PARA_API = {
  urgent: 'URGENT_IMPORTANT',
  important: 'NOT_URGENT_IMPORTANT',
  normal: 'NORMAL',
  low: 'URGENT_NOT_IMPORTANT',
};
const DA_API = Object.fromEntries(Object.entries(PARA_API).map(([k, v]) => [v, k]));

export function prioridadeParaApi(nivel) {
  return PARA_API[nivel] || 'NORMAL';
}

export function prioridadeDaApi(valor) {
  return DA_API[valor] || 'normal';
}

const ORDEM = Object.fromEntries(NIVEIS.map((n, i) => [n, i]));

export function normalizar(p) {
  return p in ORDEM ? p : 'normal';
}

// Compara duas tarefas: concluídas ao fim, depois por prioridade
export function comparar(a, b) {
  return (a.done - b.done) || (ORDEM[normalizar(a.prioridade)] - ORDEM[normalizar(b.prioridade)]);
}

// Agrupa tarefas por nível (mantendo a ordem canônica), omitindo grupos vazios
export function agrupar(tarefas) {
  return NIVEIS
    .map((nivel) => ({
      nivel,
      rotulo: ROTULO[nivel],
      cor: COR[nivel],
      itens: tarefas.filter((t) => normalizar(t.prioridade) === nivel),
    }))
    .filter((g) => g.itens.length);
}
