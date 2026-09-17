// Lado esquerdo da home: a landing de marketing (port de index.html + landing.js).
// Os ícones vinham do Lucide via CDN; aqui usam o Ic do design system, sem
// dependência externa.
import { useState } from 'react';
import Ic, { ICONS, Mark } from '@/components/Ic';
import FeatureShowcase from '@/features/landing/components/FeatureShowcase';
import { alternarTema } from '@/lib/theme';
import { LegalModal, RecursosOverlay, SobreOverlay } from '@/features/landing/components/Overlays';

const FEATURES = [
  { icone: ICONS.sun, titulo: 'Comece pelo que importa', texto: 'Filtre suas tarefas que são prioridade e foque no essencial.' },
  { icone: ICONS.clock, titulo: 'Períodos de Foco', texto: 'Acompanhe o seu ritmo ou controle suas pausas através do cronômetro.' },
  { icone: ICONS.folderTree, titulo: 'Projetos organizados', texto: 'Agrupe por contexto e veja o progresso de cada projeto num relance.' },
];

export default function LandingPane({ onCriarConta }) {
  const [recursos, setRecursos] = useState(false);
  const [sobre, setSobre] = useState(false);
  const [legal, setLegal] = useState(null); // 'termos' | 'privacidade' | null

  return (
    <div className="mkt">
      <header className="mkt-nav">
        <div className="mkt__wrap mkt-nav__in">
          <span className="mkt-brand">
            <span className="mkt-brand__mark"><Mark size={20} /></span>
            <span className="mkt-brand__word">JustDoIt</span>
          </span>
          <nav className="mkt-nav__links">
            <a className="mkt-nav__link" onClick={() => setRecursos(true)}>Recursos</a>
            <a className="mkt-nav__link" onClick={() => setSobre(true)}>Sobre</a>
          </nav>
          <div className="mkt-nav__cta">
            <button className="mkt-theme" onClick={alternarTema} aria-label="Alternar tema">
              <Ic d={ICONS.moon} />
            </button>
          </div>
        </div>
      </header>

      <section className="mkt__wrap mkt-hero">
        <div>
          <span className="mkt-hero__eyebrow">
            <span className="badge badge--info"><span className="badge__dot" />Gerenciador de tarefas</span>
          </span>
          <h1>Concentre-se no <em>que importa.</em></h1>
          <p className="mkt-hero__sub">
            O JustDoIt deixa o seu dia leve: uma lista clara e a calma de saber exatamente o próximo passo.
          </p>
          <div className="mkt-hero__actions">
            <button className="btn btn--primary btn--lg" onClick={onCriarConta}>
              Começar de graça <Ic d={ICONS.arrowUp} size={16} />
            </button>
          </div>
        </div>

        {/* Vitrine animada das funcionalidades reais (era um mock estático) */}
        <FeatureShowcase />
      </section>

      <section className="mkt__wrap mkt-features">
        <div className="mkt-grid3">
          {FEATURES.map((f) => (
            <article className="mkt-feat" key={f.titulo}>
              <div className="mkt-feat__ic"><Ic d={f.icone} /></div>
              <h3>{f.titulo}</h3>
              <p>{f.texto}</p>
            </article>
          ))}
        </div>
      </section>

      <footer className="mkt-foot">
        <div className="mkt__wrap mkt-foot__in">
          <div className="mkt-foot__brand">
            <span className="mkt-brand__mark"><Mark size={20} /></span>
            <div>
              <div className="mkt-brand__word mkt-brand__word--foot">JustDoIt</div>
              <div className="mkt-foot__tag">Gerenciador de Tarefas</div>
            </div>
          </div>
          <nav className="mkt-foot__links">
            <a onClick={() => setLegal('termos')}>Termos</a>
            <a onClick={() => setLegal('privacidade')}>Privacidade</a>
          </nav>
        </div>
      </footer>

      <RecursosOverlay aberto={recursos} onFechar={() => setRecursos(false)} />
      <SobreOverlay aberto={sobre} onFechar={() => setSobre(false)} />
      <LegalModal chave={legal} onFechar={() => setLegal(null)} />
    </div>
  );
}
