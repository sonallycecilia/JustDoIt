// Barras agrupadas por dia da semana, três séries: agendado (calendário),
// estimado (tarefas) e executado (sessões de foco). SVG puro, sem dependências.
//
// As três existem separadas de propósito: a distância entre agendado e estimado
// mostra quanto do que você planejou virou horário marcado, e a distância até o
// executado mostra o que de fato aconteceu.
const SERIES = [
  { chave: 'agendado', rotulo: 'Agendado', cor: 'var(--color-border-strong)' },
  { chave: 'estimado', rotulo: 'Estimado', cor: 'var(--color-text-subtle)' },
  { chave: 'executado', rotulo: 'Executado', cor: 'var(--color-accent)' },
];

export default function DeviationChart({ dados }) {
  const W = 560, H = 240, padX = 36, padY = 28;
  const innerW = W - padX * 2, innerH = H - padY * 2;

  // Sem nenhum valor a escala seria 0 e todo y viraria NaN — o piso de 1h
  // mantém o eixo legível numa semana ainda vazia.
  const pico = Math.max(...dados.flatMap((d) => SERIES.map((s) => d[s.chave] || 0)), 0);
  const max = (pico > 0 ? pico : 1) * 1.1;

  const grupoW = innerW / dados.length;
  const barW = Math.min(12, grupoW / 4.2);
  const y = (v) => padY + innerH - (v / max) * innerH;

  const ticks = [0, 1, 2, 3, 4, 6, 8].filter((t) => t <= max);

  return (
    <svg className="an-chart" viewBox={`0 0 ${W} ${H}`} role="img"
         aria-label="Agendado, estimado e executado por dia da semana">
      {ticks.map((t) => (
        <g key={t}>
          <line x1={padX} x2={W - padX} y1={y(t)} y2={y(t)} stroke="var(--color-border)" strokeWidth="1" />
          <text x={padX - 8} y={y(t) + 4} textAnchor="end" fontSize="10">{t}h</text>
        </g>
      ))}
      {dados.map((d, i) => {
        // As três barras do dia ficam centradas no grupo: a do meio no centro,
        // as outras deslocadas de uma largura + folga para cada lado.
        const cx = padX + grupoW * i + grupoW / 2;
        return (
          <g key={d.dia}>
            {SERIES.map((s, j) => {
              const valor = d[s.chave] || 0;
              const x = cx + (j - 1) * (barW + 2) - barW / 2;
              return (
                <rect key={s.chave} x={x} y={y(valor)} width={barW}
                      height={padY + innerH - y(valor)} rx="3" fill={s.cor}>
                  <title>{`${s.rotulo}: ${valor.toFixed(1)}h`}</title>
                </rect>
              );
            })}
            <text x={cx} y={H - 8} textAnchor="middle" fontSize="10">{d.dia}</text>
          </g>
        );
      })}
    </svg>
  );
}

export { SERIES };
