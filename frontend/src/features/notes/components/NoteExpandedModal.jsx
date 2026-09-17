import { useEffect, useRef, useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import CategorySelect from '@/features/categories/components/CategorySelect';
import RichTextEditor from '@/features/notes/components/RichTextEditor';
import { categoriaPorId } from '@/features/categories/hooks/useCategories';
import { useAtualizarNota } from '@/features/notes/hooks/useNotas';
import { useModalA11y } from '@/hooks/useModalA11y';

export default function NoteExpandedModal({ nota, categorias, onFechar }) {
  const [titulo, setTitulo] = useState('');
  const [conteudo, setConteudo] = useState('');
  const [categoria, setCategoria] = useState(null);
  const dialogRef = useRef(null);
  const titleRef = useRef(null);
  const atualizar = useAtualizarNota();

  useEffect(() => {
    if (!nota) return;
    setTitulo(nota.titulo);
    setConteudo(nota.conteudo);
    setCategoria(categoriaPorId(categorias, nota.categoriaId));
  }, [nota, categorias]);

  useModalA11y({
    aberto: Boolean(nota),
    containerRef: dialogRef,
    initialFocusRef: titleRef,
    onFechar,
    closeOnEscape: !atualizar.isPending,
  });

  if (!nota) return null;

  function salvar() {
    atualizar.mutate({
      id: nota.id,
      titulo,
      conteudo,
      categoriaId: categoria?.id || null,
    }, { onSuccess: onFechar });
  }

  return (
    <div className="note-modal">
      <div className="note-modal__backdrop" onClick={() => !atualizar.isPending && onFechar()} />
      <section
        ref={dialogRef}
        className="note-modal__dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="note-modal-title"
        tabIndex={-1}
      >
        <header className="note-modal__head">
          <div>
            <span className="note-modal__eyebrow">Anotação expandida</span>
            <h2 id="note-modal-title">Editar anotação</h2>
          </div>
          <button className="note-act" type="button" aria-label="Fechar anotação" onClick={onFechar} disabled={atualizar.isPending}>
            <Ic d={ICONS.close} />
          </button>
        </header>

        <div className="note-modal__body">
          <label className="note-modal__field">
            <span>Título</span>
            <input ref={titleRef} maxLength={255} value={titulo} onChange={(e) => setTitulo(e.target.value)} placeholder="Título (opcional)" />
          </label>
          <div className="note-modal__field">
            <span>Categoria</span>
            <CategorySelect categorias={categorias} valor={categoria?.nome} onChange={setCategoria} />
          </div>
          <div className="note-modal__editor" aria-label="Conteúdo da anotação">
            <RichTextEditor value={conteudo} onChange={setConteudo} placeholder="Conteúdo da nota…" />
          </div>
        </div>

        <footer className="note-modal__actions">
          {atualizar.isError && <span className="note-modal__error" role="alert">Não foi possível salvar. Tente novamente.</span>}
          <button className="btn btn--secondary btn--sm" type="button" onClick={onFechar} disabled={atualizar.isPending}>Cancelar</button>
          <button className="btn btn--primary btn--sm" type="button" onClick={salvar} disabled={atualizar.isPending}>
            {atualizar.isPending ? 'Salvando…' : 'Salvar alterações'}
          </button>
        </footer>
      </section>
    </div>
  );
}
