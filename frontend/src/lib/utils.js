// Utilitários de data/formatação portados de scripts/core/utils.js.
export const DIAS_CURTOS = ['DOM', 'SEG', 'TER', 'QUA', 'QUI', 'SEX', 'SÁB'];
export const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];
export const MESES_LONGOS = ['janeiro', 'fevereiro', 'março', 'abril', 'maio', 'junho', 'julho', 'agosto', 'setembro', 'outubro', 'novembro', 'dezembro'];

export function hoje() {
  const d = new Date();
  d.setHours(0, 0, 0, 0);
  return d;
}

// "Qui, 7 jun"
export function dataCurta(data = new Date()) {
  const dia = DIAS_CURTOS[data.getDay()];
  return `${dia[0] + dia.slice(1).toLowerCase()}, ${data.getDate()} ${MESES[data.getMonth()]}`;
}

// "Hoje", "Amanhã" ou "Qua, 10 jun"
export function dataRelativa(data) {
  const h = hoje();
  const amanha = new Date(h);
  amanha.setDate(h.getDate() + 1);
  if (data.toDateString() === h.toDateString()) return 'Hoje';
  if (data.toDateString() === amanha.toDateString()) return 'Amanhã';
  return dataCurta(data);
}

// Classifica uma data: 'past' | 'today' | 'week' | 'all'
export function calcQuando(data) {
  const h = hoje();
  const fim = new Date(h);
  fim.setDate(h.getDate() + 7);
  if (data < h) return 'past';
  if (data.toDateString() === h.toDateString()) return 'today';
  if (data <= fim) return 'week';
  return 'all';
}

// Date → "2026-06-09"
export function dataIso(data) {
  return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
}

// "2026-06-09" → Date local (meia-noite)
export function deIso(iso) {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(y, m - 1, d);
}

// "Bom dia" | "Boa tarde" | "Boa noite"
export function saudacao(data = new Date()) {
  const h = data.getHours();
  if (h >= 5 && h < 12) return 'Bom dia';
  if (h >= 12 && h < 18) return 'Boa tarde';
  return 'Boa noite';
}

// Semana (segunda a domingo) que contém dataBase. Este intervalo precisa usar
// a mesma convenção do schedule-service: weekly_plan.week_start_date identifica
// cada plano pela segunda-feira.
export function intervaloSemana(dataBase) {
  const d = dataBase ? new Date(dataBase) : new Date();
  const recuo = d.getDay() === 0 ? 6 : d.getDay() - 1;
  const seg = new Date(d);
  seg.setDate(d.getDate() - recuo);
  seg.setHours(0, 0, 0, 0);
  const dom = new Date(seg);
  dom.setDate(seg.getDate() + 6);
  return {
    inicio: seg,
    fim: dom,
    inicioIso: dataIso(seg),
    fimIso: dataIso(dom),
    rotulo: `${seg.getDate()} ${MESES[seg.getMonth()]} – ${dom.getDate()} ${MESES[dom.getMonth()]}`,
  };
}

// horas decimais → "2h30" / "3h"
export function horas(decimal) {
  const h = Math.floor(decimal);
  const m = Math.round((decimal - h) * 60);
  return m ? `${h}h${String(m).padStart(2, '0')}` : `${h}h`;
}

// Percentual seguro 0–100
export function pct(parte, total) {
  if (!total) return 0;
  return Math.max(0, Math.min(100, Math.round((parte / total) * 100)));
}

// "ana silva" → "Ana Silva"
export function capitalizarNome(nome) {
  if (!nome) return '';
  return nome.trim().split(/\s+/)
    .map((p) => p.charAt(0).toUpperCase() + p.slice(1).toLowerCase())
    .join(' ');
}

// "Ana Silva" → "AS"
export function iniciais(nome) {
  if (!nome) return '–';
  const partes = nome.trim().split(/\s+/);
  return (partes[0].charAt(0) + (partes.length > 1 ? partes[partes.length - 1].charAt(0) : '')).toUpperCase();
}

// segundos → "01:23:45" ou "23:45"
export function formatarTempo(seg) {
  const h = Math.floor(seg / 3600);
  const m = Math.floor((seg % 3600) / 60);
  const s = seg % 60;
  const mm = String(m).padStart(2, '0');
  const ss = String(s).padStart(2, '0');
  return h ? `${String(h).padStart(2, '0')}:${mm}:${ss}` : `${mm}:${ss}`;
}

// (14, 30) → "14:30". Não confundir com o fmtHora de hooks/useTimeBlocks,
// que converte HORAS DECIMAIS (14.5) — o calendário usa fração de hora.
export function fmtHoraMin(h, m) {
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}`;
}

// segundos → "25:00" (mm:ss, para o Pomodoro)
export function formatarMinSeg(seg) {
  const m = Math.floor(seg / 60);
  const s = seg % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}
