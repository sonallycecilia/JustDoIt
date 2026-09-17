import { useEffect } from 'react';
import Color from '@tiptap/extension-color';
import Placeholder from '@tiptap/extension-placeholder';
import { FontSize, TextStyle } from '@tiptap/extension-text-style';
import { EditorContent, useEditor } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';

export const RICH_TEXT_PREFIX = 'jdi:rich-text:';

function serializar(editor) {
  return editor.isEmpty ? '' : `${RICH_TEXT_PREFIX}${JSON.stringify(editor.getJSON())}`;
}

function conteudoDoEditor(value) {
  if (!value?.startsWith(RICH_TEXT_PREFIX)) return value || '';
  try { return JSON.parse(value.slice(RICH_TEXT_PREFIX.length)); } catch { return ''; }
}

function ToolButton({ active = false, title, onClick, children }) {
  return <button className={`rich-editor__button ${active ? 'is-active' : ''}`} type="button" title={title} aria-label={title} aria-pressed={active} onClick={onClick}>{children}</button>;
}

export default function RichTextEditor({ value, onChange, placeholder = 'Escreva sua anotação…', onKeyDown, autoFocus = false }) {
  const editor = useEditor({
    extensions: [StarterKit, TextStyle, Color, FontSize, Placeholder.configure({ placeholder })],
    content: conteudoDoEditor(value),
    editorProps: {
      attributes: { class: 'rich-editor__content', 'data-placeholder': placeholder },
      handleKeyDown: (_view, event) => {
        if (onKeyDown) onKeyDown(event);
        return event.defaultPrevented;
      },
    },
    onUpdate: ({ editor: current }) => onChange(serializar(current)),
    immediatelyRender: false,
    shouldRerenderOnTransaction: true,
  });

  useEffect(() => {
    if (!editor) return;
    const atual = serializar(editor);
    if (value !== atual) {
      // Converte rascunhos antigos em HTML/texto para o formato aceito pelo backend.
      if (value && !value.startsWith(RICH_TEXT_PREFIX)) onChange(atual);
      else editor.commands.setContent(conteudoDoEditor(value), { emitUpdate: false });
    }
  }, [editor, value]);

  useEffect(() => {
    if (editor && autoFocus) editor.commands.focus('end');
  }, [autoFocus, editor]);

  if (!editor) return null;

  const tamanho = editor.getAttributes('textStyle').fontSize || '';

  return (
    <div className="rich-editor">
      <div className="rich-editor__toolbar" role="toolbar" aria-label="Formatação da nota">
        <select className="rich-editor__select rich-editor__format" aria-label="Estilo do texto" value={editor.isActive('heading', { level: 1 }) ? 'h1' : editor.isActive('heading', { level: 2 }) ? 'h2' : 'p'} onChange={(e) => {
          const tipo = e.target.value;
          if (tipo === 'p') editor.chain().focus().setParagraph().run();
          else editor.chain().focus().toggleHeading({ level: Number(tipo.slice(1)) }).run();
        }}>
          <option value="p">Texto normal</option>
          <option value="h1">Título 1</option>
          <option value="h2">Título 2</option>
        </select>
        <select className="rich-editor__select" aria-label="Tamanho da fonte" value={tamanho} onChange={(e) => {
          const px = e.target.value;
          const chain = editor.chain().focus();
          if (px) chain.setFontSize(px).run(); else chain.unsetFontSize().run();
        }}>
          <option value="">Tamanho</option>
          <option value="12px">12</option><option value="14px">14</option><option value="16px">16</option><option value="20px">20</option><option value="24px">24</option><option value="32px">32</option>
        </select>
        <span className="rich-editor__separator" />
        <ToolButton title="Negrito" active={editor.isActive('bold')} onClick={() => editor.chain().focus().toggleBold().run()}><strong>B</strong></ToolButton>
        <ToolButton title="Itálico" active={editor.isActive('italic')} onClick={() => editor.chain().focus().toggleItalic().run()}><em>I</em></ToolButton>
        <ToolButton title="Sublinhado" active={editor.isActive('underline')} onClick={() => editor.chain().focus().toggleUnderline().run()}><u>U</u></ToolButton>
        <label className="rich-editor__color" title="Cor do texto">
          <span>A</span>
          <input type="color" aria-label="Cor do texto" value={editor.getAttributes('textStyle').color || '#252525'} onChange={(e) => editor.chain().focus().setColor(e.target.value).run()} />
        </label>
        <span className="rich-editor__separator" />
        <ToolButton title="Lista com marcadores" active={editor.isActive('bulletList')} onClick={() => editor.chain().focus().toggleBulletList().run()}>• Lista</ToolButton>
        <ToolButton title="Lista numerada" active={editor.isActive('orderedList')} onClick={() => editor.chain().focus().toggleOrderedList().run()}>1. Lista</ToolButton>
        <ToolButton title="Citação" active={editor.isActive('blockquote')} onClick={() => editor.chain().focus().toggleBlockquote().run()}>“</ToolButton>
      </div>
      <EditorContent editor={editor} />
    </div>
  );
}
