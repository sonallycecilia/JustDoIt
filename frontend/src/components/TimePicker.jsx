import { useEffect } from 'react';

const fmt = (n) => String(n).padStart(2, '0');

// Seletor de hora portado de components/time-picker.js (mesmas classes CSS).
// Controlado: recebe { hora, min } e reporta via onChange(h, m) / onClear().
export default function TimePicker({ aberto, onFechar, hora, min, onChange, onClear }) {
  useEffect(() => {
    if (!aberto) return;
    const onKey = (e) => { if (e.key === 'Escape') onFechar(); };
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [aberto, onFechar]);

  if (!aberto) return null;

  const horas = Array.from({ length: 24 }, (_, h) => h);

  return (
    <>
      <div className="time-pick__overlay" onClick={onFechar} />
      <div className="time-pick__menu">
        <div className="time-pick__section-label">Hora</div>
        <div className="time-pick__hours">
          {horas.map((h) => (
            <button
              key={h}
              type="button"
              className={`time-pick__hour ${h === hora ? 'is-on' : ''}`}
              onClick={() => onChange(h, min ?? 0)}
            >
              {fmt(h)}
            </button>
          ))}
        </div>
        <div className="time-pick__section-label" style={{ marginTop: 4 }}>Minuto</div>
        <div className="time-pick__mins">
          {[0, 15, 30, 45].map((m) => (
            <button
              key={m}
              type="button"
              className={`time-pick__min ${m === min ? 'is-on' : ''}`}
              onClick={() => onChange(hora, m)}
            >
              :{fmt(m)}
            </button>
          ))}
        </div>
        <hr className="time-pick__divider" />
        <button type="button" className="time-pick__clear" onClick={() => { onFechar(); onClear(); }}>
          Remover hora
        </button>
      </div>
    </>
  );
}
