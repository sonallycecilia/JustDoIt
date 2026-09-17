import { Fragment } from 'react';
import { RICH_TEXT_PREFIX } from '@/features/notes/components/RichTextEditor';

function aplicarMarcas(conteudo, marcas = []) {
  return marcas.reduce((filho, marca, indice) => {
    const key = `${marca.type}-${indice}`;
    if (marca.type === 'bold') return <strong key={key}>{filho}</strong>;
    if (marca.type === 'italic') return <em key={key}>{filho}</em>;
    if (marca.type === 'underline') return <u key={key}>{filho}</u>;
    if (marca.type === 'strike') return <s key={key}>{filho}</s>;
    if (marca.type === 'code') return <code key={key}>{filho}</code>;
    if (marca.type === 'textStyle') return <span key={key} style={{ color: marca.attrs?.color, fontSize: marca.attrs?.fontSize }}>{filho}</span>;
    return filho;
  }, conteudo);
}

function renderizarNo(no, key) {
  if (no.type === 'text') return <Fragment key={key}>{aplicarMarcas(no.text || '', no.marks)}</Fragment>;
  if (no.type === 'hardBreak') return <br key={key} />;
  const filhos = (no.content || []).map((filho, indice) => renderizarNo(filho, `${key}-${indice}`));
  if (no.type === 'paragraph') return <p key={key}>{filhos}</p>;
  if (no.type === 'heading') {
    const Nivel = `h${Math.min(Math.max(no.attrs?.level || 1, 1), 6)}`;
    return <Nivel key={key}>{filhos}</Nivel>;
  }
  if (no.type === 'bulletList') return <ul key={key}>{filhos}</ul>;
  if (no.type === 'orderedList') return <ol key={key} start={no.attrs?.start || 1}>{filhos}</ol>;
  if (no.type === 'listItem') return <li key={key}>{filhos}</li>;
  if (no.type === 'blockquote') return <blockquote key={key}>{filhos}</blockquote>;
  if (no.type === 'codeBlock') return <pre key={key}><code>{filhos}</code></pre>;
  if (no.type === 'horizontalRule') return <hr key={key} />;
  return <Fragment key={key}>{filhos}</Fragment>;
}

export default function RichTextContent({ children, className = '' }) {
  const conteudo = children || '';
  let documento = null;
  if (conteudo.startsWith(RICH_TEXT_PREFIX)) {
    try { documento = JSON.parse(conteudo.slice(RICH_TEXT_PREFIX.length)); } catch { /* exibe como legado */ }
  }

  return (
    <div className={`rich-text-content ${className}`.trim()}>
      {documento
        ? <div>{renderizarNo(documento, 'doc')}</div>
        : <div className="rich-text-legacy">{conteudo.replace(/<[^>]*>/g, '')}</div>}
    </div>
  );
}
