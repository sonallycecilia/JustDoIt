import { useEffect, useRef, useState } from 'react';
import { FORMATOS } from '@/features/settings/lib/exportacao';
import { useModalA11y } from '@/hooks/useModalA11y';

// Janela de confirmação da exportação: escolha do formato + o que vai no arquivo.
// Reusa as classes .cat-modal (mesmo diálogo da sidebar e do ConfirmModal); não
// dá para usar o ConfirmModal direto porque ali o botão principal é destrutivo.
export default function ExportModal({ aberto, processando = false, erro, onExportar, onFechar }) {
  const [formato, setFormato] = useState(FORMATOS[0].valor);
  const dialogRef = useRef(null);
  const cancelRef = useRef(null);

  useEffect(() => { if (aberto) setFormato(FORMATOS[0].valor); }, [aberto]);

  useModalA11y({ aberto, containerRef: dialogRef, initialFocusRef: cancelRef, onFechar, closeOnEscape: !processando });

  if (!aberto) return null;

  return (
    <div className="cat-modal">
      <div className="cat-modal__backdrop" onClick={() => !processando && onFechar()} />
      <form
        ref={dialogRef}
        className="cat-modal__card"
        role="dialog"
        aria-modal="true"
        aria-labelledby="export-modal-title"
        tabIndex={-1}
        onSubmit={(e) => { e.preventDefault(); onExportar(formato); }}
      >
        <div className="cat-modal__head">
          <h3 id="export-modal-title" className="cat-modal__title">Exportar meus dados</h3>
        </div>

        <p className="exp-modal__text">
          O arquivo traz todas as suas tarefas com status de conclusão, categoria, datas de
          criação e conclusão, estimativa, tempo do cronômetro e o conteúdo do bloco de notas.
        </p>

        <div className="cat-modal__label">Formato</div>
        <div className="exp-modal__formats" role="radiogroup" aria-label="Formato do arquivo">
          {FORMATOS.map((f) => (
            <label key={f.valor} className={`exp-format ${formato === f.valor ? 'is-sel' : ''}`}>
              <input
                type="radio"
                name="formato-exportacao"
                value={f.valor}
                checked={formato === f.valor}
                disabled={processando}
                onChange={() => setFormato(f.valor)}
              />
              <span className="exp-format__body">
                <span className="exp-format__name">{f.rotulo}</span>
                <span className="exp-format__desc">{f.descricao}</span>
              </span>
            </label>
          ))}
        </div>

        {erro && <div className="cat-modal__error" role="alert">{erro}</div>}

        <div className="cat-modal__actions">
          <button ref={cancelRef} className="btn btn--secondary btn--sm" type="button" onClick={onFechar} disabled={processando}>
            Cancelar
          </button>
          <button className="btn btn--primary btn--sm" type="submit" disabled={processando}>
            {processando ? 'Preparando arquivo…' : 'Exportar'}
          </button>
        </div>
      </form>
    </div>
  );
}
