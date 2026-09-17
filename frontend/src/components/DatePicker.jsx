import { useEffect, useRef, useState } from 'react';
import { hoje, MESES_LONGOS } from '@/lib/utils';

const LEFT = <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m15 18-6-6 6-6" /></svg>;
const RIGHT = <svg viewBox="0 0 24 24" width="14" height="14" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="m9 18 6-6-6-6" /></svg>;

// Calendário dropdown portado de components/date-picker.js (mesmas classes CSS).
// `modoNascimento` ativa a navegação ano → mês → dia.
// Renderiza apenas o dropdown; o botão-gatilho fica por conta do chamador,
// que controla `aberto`/`onFechar` e recebe `onSelect(date)`.
export default function DatePicker({ aberto, onFechar, onSelect, selecionada, modoNascimento = false }) {
  const h = hoje();
  const dataSel = selecionada || h;
  const [view, setView] = useState({ year: dataSel.getFullYear(), month: dataSel.getMonth() });
  const [modo, setModo] = useState(modoNascimento ? 'anos' : 'dias');
  const anoSelRef = useRef(null);

  useEffect(() => {
    if (aberto) {
      setView({ year: dataSel.getFullYear(), month: dataSel.getMonth() });
      setModo(modoNascimento ? 'anos' : 'dias');
    }
  }, [aberto]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!aberto) return;
    const onKey = (e) => { if (e.key === 'Escape') onFechar(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [aberto, onFechar]);

  useEffect(() => {
    if (modo === 'anos') anoSelRef.current?.scrollIntoView({ block: 'center' });
  }, [modo, aberto]);

  if (!aberto) return null;

  const anoAtual = h.getFullYear();
  const anos = [];
  for (let y = anoAtual; y >= anoAtual - 100; y--) anos.push(y);

  const firstDow = new Date(view.year, view.month, 1).getDay();
  const totalDias = new Date(view.year, view.month + 1, 0).getDate();
  const dias = [];
  for (let d = 1; d <= totalDias; d++) dias.push(d);

  return (
    <>
      <div className="date-pick__overlay" onClick={onFechar} />
      <div className="date-pick__menu">
        {modo === 'anos' && (
          <>
            <div className="date-pick__head">
              <span className="date-pick__month">Selecionar ano</span>
            </div>
            <div className="date-pick__years-grid">
              {anos.map((y) => (
                <button
                  key={y}
                  type="button"
                  ref={y === view.year ? anoSelRef : null}
                  className={`date-pick__year-btn ${y === view.year ? 'is-on' : ''}`}
                  onClick={() => { setView((v) => ({ ...v, year: y })); setModo('meses'); }}
                >
                  {y}
                </button>
              ))}
            </div>
          </>
        )}

        {modo === 'meses' && (
          <>
            <div className="date-pick__head">
              <button type="button" className="date-pick__nav" onClick={() => setModo('anos')}>{LEFT}</button>
              <span className="date-pick__month date-pick__month--link" onClick={() => setModo('anos')}>{view.year}</span>
            </div>
            <div className="date-pick__months-grid">
              {MESES_LONGOS.map((nome, idx) => (
                <button
                  key={nome}
                  type="button"
                  className={`date-pick__month-btn ${idx === view.month ? 'is-on' : ''}`}
                  onClick={() => { setView((v) => ({ ...v, month: idx })); setModo('dias'); }}
                >
                  {nome.substring(0, 3)}
                </button>
              ))}
            </div>
          </>
        )}

        {modo === 'dias' && (
          <>
            <div className="date-pick__head">
              {modoNascimento ? (
                <button type="button" className="date-pick__nav" onClick={() => setModo('meses')}>{LEFT}</button>
              ) : (
                <button
                  type="button"
                  className="date-pick__nav"
                  onClick={() => setView((v) => (v.month === 0 ? { year: v.year - 1, month: 11 } : { ...v, month: v.month - 1 }))}
                >
                  {LEFT}
                </button>
              )}
              <span
                className={`date-pick__month ${modoNascimento ? 'date-pick__month--link' : ''}`}
                onClick={modoNascimento ? () => setModo('meses') : undefined}
              >
                {MESES_LONGOS[view.month]} {view.year}
              </span>
              {!modoNascimento && (
                <button
                  type="button"
                  className="date-pick__nav"
                  onClick={() => setView((v) => (v.month === 11 ? { year: v.year + 1, month: 0 } : { ...v, month: v.month + 1 }))}
                >
                  {RIGHT}
                </button>
              )}
            </div>
            <div className="date-pick__grid">
              {['D', 'S', 'T', 'Q', 'Q', 'S', 'S'].map((l, i) => <div className="date-pick__dow" key={i}>{l}</div>)}
              {Array.from({ length: firstDow }).map((_, i) => <div key={`v${i}`} />)}
              {dias.map((d) => {
                const date = new Date(view.year, view.month, d);
                const isToday = date.toDateString() === h.toDateString();
                const isOn = date.toDateString() === dataSel.toDateString();
                return (
                  <button
                    key={d}
                    type="button"
                    className={`date-pick__day ${isToday ? 'is-today' : ''} ${isOn ? 'is-on' : ''}`}
                    onClick={() => { onFechar(); onSelect(date); }}
                  >
                    {d}
                  </button>
                );
              })}
            </div>
          </>
        )}
      </div>
    </>
  );
}
