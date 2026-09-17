// Aba "Anotações": todas as notas do usuário (fixada primeiro, ordem do
// servidor), com categorias, visualização expandida, edição e exclusão.
import { useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import NoteComposer from '@/features/notes/components/NoteComposer';
import RichTextContent from '@/features/notes/components/RichTextContent';
import NoteExpandedModal from '@/features/notes/components/NoteExpandedModal';
import Sidebar from '@/components/Sidebar';
import { categoriaPorId, useCategorias } from '@/features/categories/hooks/useCategories';
import { useFixarNota, useNotas, useRemoverNota } from '@/features/notes/hooks/useNotas';

function formatarData(iso) {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const dia = String(d.getDate()).padStart(2, '0');
  const mes = String(d.getMonth() + 1).padStart(2, '0');
  const hh = String(d.getHours()).padStart(2, '0');
  const mm = String(d.getMinutes()).padStart(2, '0');
  return `${dia}/${mes}/${d.getFullYear()} ${hh}:${mm}`;
}

// Título exibido: o próprio título, ou a 1ª linha não vazia do conteúdo.
function tituloDe(n) {
  if (n.titulo && n.titulo.trim()) return n.titulo.trim();
  const texto = (n.conteudo || '')
    .replace(/^jdi:rich-text:/, '')
    .replace(/"text":"([^"]+)"/g, '$1\n')
    .replace(/<[^>]*>/g, ' ');
  const linha = texto.split('\n').map((l) => l.trim()).find(Boolean);
  return linha || 'Sem título';
}

function NoteCard({ nota, categorias, onExpandir }) {
  // 'normal' | 'confirmando'
  const [modo, setModo] = useState('normal');
  const remover = useRemoverNota();
  const fixar = useFixarNota();
  const categoria = categoriaPorId(categorias, nota.categoriaId);

  if (modo === 'confirmando') {
    return (
      <article className="note-card note-card--confirming">
        <p className="note-confirm__msg">Excluir esta anotação? Não dá para desfazer.</p>
        <div className="note-edit__actions">
          <button className="btn btn--secondary btn--sm" type="button" onClick={() => setModo('normal')}>Cancelar</button>
          <button
            className="btn btn--danger btn--sm"
            type="button"
            disabled={remover.isPending}
            onClick={() => remover.mutate(nota.id)}
          >
            Excluir
          </button>
        </div>
      </article>
    );
  }

  return (
    <article className={`note-card ${nota.fixada ? 'note-card--pinned' : ''} ${fixar.isPending ? 'is-busy' : ''}`}>
      <div className="note-card__top">
        <div>
          <h3 className="note-card__title">{tituloDe(nota)}</h3>
          <span className="note-card__category"><i style={{ background: categoria.cor }} />{categoria.nome}</span>
        </div>
        {nota.fixada && <span className="note-card__pin-badge"><Ic d={ICONS.pin} />Fixada</span>}
      </div>
      {nota.conteudo && <RichTextContent className="note-card__body">{nota.conteudo}</RichTextContent>}
      <div className="note-card__foot">
        <span className="note-card__date">{formatarData(nota.atualizadaEm || nota.criadaEm)}</span>
        <div className="note-card__actions">
          <button
            className={`note-act ${nota.fixada ? 'is-on' : ''}`}
            title={nota.fixada ? 'Desafixar' : 'Fixar no topo'}
            aria-label="Fixar"
            disabled={fixar.isPending}
            onClick={() => fixar.mutate(nota.id)}
          >
            <Ic d={ICONS.pin} />
          </button>
          <button
            className="note-act"
            title="Abrir e editar"
            aria-label={`Abrir e editar ${tituloDe(nota)}`}
            onClick={() => onExpandir(nota)}
          >
            <Ic d={ICONS.edit} />
          </button>
          <button className="note-act note-act--danger" title="Excluir" aria-label="Excluir" onClick={() => setModo('confirmando')}>
            <Ic d={ICONS.trash} />
          </button>
        </div>
      </div>
    </article>
  );
}

export default function Anotacoes() {
  const { data: notas, isLoading, isError, refetch } = useNotas();
  const { data: categorias = [] } = useCategorias();
  const [notaExpandida, setNotaExpandida] = useState(null);

  return (
    <div className="app">
      <Sidebar ativa="notes" />

      <main className="app__main">
        <div className="page">
          <header className="page__head">
            <div>
              <h1 className="page__title">Anotações</h1>
              <div className="page__eyebrow">Todas as suas notas</div>
            </div>
          </header>

          <NoteComposer />

          <div className="notes-head">
            <span className="notes-head__title">Minhas anotações</span>
            <span className="notes-head__count">{notas?.length ?? 0}</span>
          </div>

          {isLoading && <div className="empty"><p>Carregando anotações…</p></div>}

          {isError && (
            <div className="empty">
              <p>Sem conexão com o servidor — não foi possível carregar suas anotações.</p>
              <button className="btn btn--secondary btn--md" onClick={() => refetch()}>Tentar de novo</button>
            </div>
          )}

          {!isLoading && !isError && (notas || []).length === 0 && (
            <div className="empty">
              <Ic d={ICONS.edit} />
              <p>Nenhuma anotação ainda — comece pelo bloco acima.</p>
            </div>
          )}

          <div className="notes-list">
            {notas?.map((n) => <NoteCard key={n.id} nota={n} categorias={categorias} onExpandir={setNotaExpandida} />)}
          </div>
        </div>
      </main>
      <NoteExpandedModal nota={notaExpandida} categorias={categorias} onFechar={() => setNotaExpandida(null)} />
    </div>
  );
}
