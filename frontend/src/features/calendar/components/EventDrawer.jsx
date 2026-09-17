// Painel lateral do calendário (port do PainelDrawer, sem iframe): shell
// redimensionável arrastando a borda esquerda; o conteúdo vem como children.
import { useEffect, useRef, useState } from 'react';

const DRAWER_WIDTH_KEY = 'jdi-calendar-drawer-width';
const DRAWER_DEFAULT = 520;
const DRAWER_MIN = 380;
const DRAWER_MAX = 900;

function limitesDrawer() {
  const max = Math.max(1, Math.min(DRAWER_MAX, window.innerWidth * 0.95));
  return { min: Math.min(DRAWER_MIN, max), max };
}

function limitarLarguraDrawer(largura) {
  const { min, max } = limitesDrawer();
  return Math.min(max, Math.max(min, largura));
}

function larguraInicialDrawer() {
  const salva = Number.parseFloat(localStorage.getItem(DRAWER_WIDTH_KEY));
  return limitarLarguraDrawer(Number.isFinite(salva) ? salva : DRAWER_DEFAULT);
}

export default function EventDrawer({ onClose, children }) {
  const [width, setWidth] = useState(larguraInicialDrawer);
  const [resizing, setResizing] = useState(false);
  const resizeStart = useRef({ pointerId: null, x: 0, w: 0 });
  const currentWidth = useRef(width);

  useEffect(() => {
    if (!resizing) return undefined;

    const previousCursor = document.body.style.cursor;
    const previousUserSelect = document.body.style.userSelect;
    document.body.style.cursor = 'ew-resize';
    document.body.style.userSelect = 'none';

    function onMove(e) {
      if (e.pointerId !== resizeStart.current.pointerId) return;
      const delta = resizeStart.current.x - e.clientX;
      const nextWidth = limitarLarguraDrawer(resizeStart.current.w + delta);
      currentWidth.current = nextWidth;
      setWidth(nextWidth);
    }
    function onUp(e) {
      if (e.pointerId !== resizeStart.current.pointerId) return;
      localStorage.setItem(DRAWER_WIDTH_KEY, `${currentWidth.current}px`);
      setResizing(false);
    }
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
    document.addEventListener('pointercancel', onUp);
    return () => {
      document.removeEventListener('pointermove', onMove);
      document.removeEventListener('pointerup', onUp);
      document.removeEventListener('pointercancel', onUp);
      document.body.style.cursor = previousCursor;
      document.body.style.userSelect = previousUserSelect;
    };
  }, [resizing]);

  useEffect(() => {
    function onWindowResize() {
      setWidth((current) => {
        const next = limitarLarguraDrawer(current);
        currentWidth.current = next;
        return next;
      });
    }
    window.addEventListener('resize', onWindowResize);
    return () => window.removeEventListener('resize', onWindowResize);
  }, []);

  function startResize(e) {
    resizeStart.current = { pointerId: e.pointerId, x: e.clientX, w: width };
    currentWidth.current = width;
    e.currentTarget.setPointerCapture?.(e.pointerId);
    setResizing(true);
    e.preventDefault();
  }

  function resizeWithKeyboard(e) {
    let next;
    if (e.key === 'ArrowLeft') next = width + 16;
    if (e.key === 'ArrowRight') next = width - 16;
    if (e.key === 'Home') next = limitesDrawer().min;
    if (e.key === 'End') next = limitesDrawer().max;
    if (next === undefined) return;

    e.preventDefault();
    const limited = limitarLarguraDrawer(next);
    currentWidth.current = limited;
    setWidth(limited);
    localStorage.setItem(DRAWER_WIDTH_KEY, `${limited}px`);
  }

  return (
    <div className={`cal-drawer is-open ${resizing ? 'is-resizing' : ''}`} style={{ width }}>
      <div
        className={`cal-drawer__resize ${resizing ? 'is-dragging' : ''}`}
        role="separator"
        aria-label="Redimensionar painel do calendário"
        aria-orientation="vertical"
        aria-valuemin={Math.round(limitesDrawer().min)}
        aria-valuemax={Math.round(limitesDrawer().max)}
        aria-valuenow={Math.round(width)}
        tabIndex={0}
        title="Arraste para redimensionar o painel do calendário"
        onPointerDown={startResize}
        onKeyDown={resizeWithKeyboard}
      />
      <div className="cal-drawer__head">
        <button className="btn-icon" onClick={onClose} aria-label="Fechar painel">
          <svg viewBox="0 0 24 24" width="16" height="16" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M18 6 6 18" /><path d="M6 6l12 12" /></svg>
        </button>
        <span className="cal-drawer__hint">arraste a borda para redimensionar</span>
      </div>
      <div className="cal-drawer__body">
        {children}
      </div>
    </div>
  );
}
