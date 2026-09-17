// Recorrência: opções da UI alinhadas ao enum CycleType do backend
// (DAILY | WEEKLY | BIWEEKLY | MONTHLY | ANNUAL | CUSTOM).
// 'custom' = ciclo personalizado ("a cada N horas/dias, X vezes, a partir de
// uma data") — os detalhes vão no CycleConfigRequest, não no enum.
export const TIPOS = ['none', 'DAILY', 'WEEKLY', 'BIWEEKLY', 'MONTHLY', 'ANNUAL', 'custom'];

const ROTULOS = {
  none: 'Não repete',
  DAILY: 'Diária',
  WEEKLY: 'Semanal',
  BIWEEKLY: 'Quinzenal',
  MONTHLY: 'Mensal',
  ANNUAL: 'Anual',
  custom: 'Personalizado',
};

export function rotuloCiclo(tipo) {
  return ROTULOS[tipo] || 'Sem recorrência';
}

// ── Ciclo personalizado ──────────────────────────────────────────────────────
// Objeto da UI: { count, unit: 'horas'|'dias', occurrences, startIso }.
export function customValido(c) {
  return Boolean(c) && Number(c.count) > 0 && Number(c.occurrences) > 0
    && (c.unit === 'horas' || c.unit === 'dias');
}

// Rótulo curto p/ o painel de especificações. Ex.: "A cada 12h · 7×".
export function rotuloCustom(c) {
  if (!customValido(c)) return 'Personalizado';
  const u = c.unit === 'horas' ? 'h' : (Number(c.count) === 1 ? ' dia' : ' dias');
  return `A cada ${c.count}${u} · ${c.occurrences}×`;
}

// Data prevista da última ocorrência (informativo do resumo).
export function fimPrevisto(c, inicio) {
  const n = Math.max(0, Number(c.occurrences) - 1);
  const step = Number(c.count) * n;
  const d = new Date(inicio);
  if (c.unit === 'horas') d.setHours(d.getHours() + step);
  else d.setDate(d.getDate() + step);
  return d;
}
