// Anel de taxa de conclusão da semana (port do inline em analytics.html).
export default function RateRing({ conclusao }) {
  const pct = conclusao.total ? Math.round((conclusao.feitas / conclusao.total) * 100) : 0;
  const r = 58, c = 2 * Math.PI * r, dash = (pct / 100) * c;

  return (
    <div className="an-rate">
      <svg className="an-rate__ring" viewBox="0 0 140 140" role="img" aria-label={`${pct}% das tarefas concluídas`}>
        <circle cx="70" cy="70" r={r} fill="none" stroke="var(--color-surface-sunken)" strokeWidth="14" />
        <circle
          cx="70" cy="70" r={r} fill="none"
          stroke="var(--color-success)" strokeWidth="14" strokeLinecap="round"
          strokeDasharray={`${dash} ${c - dash}`} transform="rotate(-90 70 70)"
        />
        <text x="70" y="76" textAnchor="middle" fontSize="30" fontWeight="600" fill="var(--color-text)" style={{ fontFamily: 'var(--font-display)' }}>
          {pct}%
        </text>
      </svg>
      <div className="an-rate__info">
        <div className="an-rate__sub">tarefas concluídas</div>
        <div className="an-rate__stats">
          <div>
            <div className="an-rate__stat-v">{conclusao.feitas}</div>
            <div className="an-rate__stat-l">concluídas</div>
          </div>
          <div>
            <div className="an-rate__stat-v">{conclusao.total - conclusao.feitas}</div>
            <div className="an-rate__stat-l">restantes</div>
          </div>
          <div>
            <div className="an-rate__stat-v">{conclusao.total}</div>
            <div className="an-rate__stat-l">no total</div>
          </div>
        </div>
      </div>
    </div>
  );
}
