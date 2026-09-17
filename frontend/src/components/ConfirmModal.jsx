import { useEffect, useRef, useState } from 'react';
import { useModalA11y } from '@/hooks/useModalA11y';

// Modal de confirmação — reusa os estilos .cat-modal da sidebar.
// Passando `exigeTexto`, o botão só habilita quando o usuário digita
// exatamente aquela palavra (usado na exclusão de conta).
export default function ConfirmModal({
  aberto,
  titulo,
  children,
  rotuloConfirmar = 'Excluir',
  rotuloProcessando = 'Excluindo…',
  exigeTexto,
  erro,
  processando = false,
  onConfirmar,
  onFechar,
}) {
  const [digitado, setDigitado] = useState('');
  const confirmRef = useRef(null);
  const dialogRef = useRef(null);

  useEffect(() => {
    if (aberto) {
      setDigitado('');
    }
  }, [aberto]);

  useModalA11y({ aberto, containerRef: dialogRef, initialFocusRef: confirmRef, onFechar, closeOnEscape: !processando });

  if (!aberto) return null;

  const liberado = !exigeTexto || digitado.trim().toUpperCase() === exigeTexto.toUpperCase();

  return (
    <div className="cat-modal">
      <div className="cat-modal__backdrop" onClick={() => !processando && onFechar()} />
      <div ref={dialogRef} className="cat-modal__card" role="dialog" aria-modal="true" aria-labelledby="confirm-modal-title" tabIndex={-1}>
        <div className="cat-modal__head">
          <h3 id="confirm-modal-title" className="cat-modal__title">{titulo}</h3>
        </div>
        {children}
        {exigeTexto && (
          <input
            className="cat-modal__input"
            placeholder={exigeTexto}
            autoComplete="off"
            value={digitado}
            onChange={(e) => setDigitado(e.target.value)}
          />
        )}
        {erro && <div className="cat-modal__error" role="alert">{erro}</div>}
        <div className="cat-modal__actions">
          <button className="btn btn--secondary btn--sm" type="button" onClick={onFechar} disabled={processando}>
            Cancelar
          </button>
          <button
            ref={confirmRef}
            className="btn btn--danger btn--sm"
            type="button"
            onClick={onConfirmar}
            disabled={!liberado || processando}
          >
            {processando ? rotuloProcessando : rotuloConfirmar}
          </button>
        </div>
      </div>
    </div>
  );
}
