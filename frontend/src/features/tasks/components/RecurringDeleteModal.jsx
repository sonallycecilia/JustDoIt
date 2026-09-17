import { useRef } from 'react';
import { useModalA11y } from '@/hooks/useModalA11y';

export default function RecurringDeleteModal({ tarefa, processando, erro, onEscolher, onFechar }) {
  const dialogRef = useRef(null);
  const cancelRef = useRef(null);
  useModalA11y({ aberto: Boolean(tarefa), containerRef: dialogRef, initialFocusRef: cancelRef, onFechar, closeOnEscape: !processando });

  if (!tarefa) return null;

  return (
    <div className="cat-modal">
      <div className="cat-modal__backdrop" onClick={() => !processando && onFechar()} />
      <div ref={dialogRef} className="cat-modal__card" role="dialog" aria-modal="true" aria-labelledby="recurring-delete-title" tabIndex={-1}>
        <div className="cat-modal__head">
          <h3 id="recurring-delete-title" className="cat-modal__title">Excluir tarefa recorrente</h3>
        </div>
        <p>Esta tarefa faz parte de uma série. O que você deseja excluir?</p>
        {erro && <div className="cat-modal__error" role="alert">{erro}</div>}
        <div className="cat-modal__actions">
          <button ref={cancelRef} className="btn btn--secondary btn--sm" type="button" onClick={onFechar} disabled={processando}>
            Cancelar
          </button>
          <button className="btn btn--danger btn--sm" type="button"
            onClick={() => onEscolher('INSTANCE')} disabled={processando}>
            Somente esta ocorrência
          </button>
          <button className="btn btn--danger btn--sm" type="button"
            onClick={() => onEscolher('SERIES')} disabled={processando}>
            Todas as ocorrências
          </button>
        </div>
      </div>
    </div>
  );
}
