// composer da nota reutilizável 
// Guarda um rascunho local enquanto digita e, ao criar, grava no backend
// (POST /notes) e limpa o bloco para a próxima.
import { useEffect, useRef, useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import RichTextEditor from '@/features/notes/components/RichTextEditor';
import CategorySelect from '@/features/categories/components/CategorySelect';
import { useCategorias, CAT_GENERICO } from '@/features/categories/hooks/useCategories';

// grava o rascunho no localStorage para não perder o que o usuário digitou se ele sair da página
import {
  gravarRascunho,
  gravarRascunhoTitulo,
  lerRascunho,
  lerRascunhoTitulo,
  limparRascunho,
  useCriarNota,
} from '@/features/notes/hooks/useNotas';

export default function NoteComposer() {
  

  const [titulo, setTitulo] = useState(() => lerRascunhoTitulo());
  const [texto, setTexto] = useState(() => lerRascunho());
  const [categoria, setCategoria] = useState(CAT_GENERICO);
  const { data: categorias = [] } = useCategorias();
  
  // Estado para a mensagem de "feedback" ("salvo automaticamente").
  const [hint, setHint] = useState(null); 
  
  const hintTimer = useRef(null);
  
  // conecta com o backend para criar a nota (POST /notes) e devolve o status da requisição
  const criar = useCriarNota();

  // quando o usuário sai dessa página e o componente morre,
  // ele limpa o cronômetro para evitar erros no navegador.
  useEffect(() => () => clearTimeout(hintTimer.current), []);

  /**
   * Função auxiliar para piscar uma mensagem na tela e sumir depois.
   */
  function flashHint(txt, ms = 2000) {
    setHint(txt);
    clearTimeout(hintTimer.current); // Se o usuário digitar muito rápido, cancela o timer anterior e cria um novo
    hintTimer.current = setTimeout(() => setHint(null), ms); 
  }

  // Quando o usuário digita no campo de Título:
  function aoDigitarTitulo(e) {
    setTitulo(e.target.value); // 1. Atualiza a tela.
    gravarRascunhoTitulo(e.target.value); // 2. Salva silenciosamente no navegador.
    flashHint('salvo automaticamente'); // 3. Dá o feedback visual.
  }

  // Quando o usuário digita na área de texto:
  function aoDigitar(e) {
    setTexto(e.target.value);
    gravarRascunho(e.target.value);
    flashHint('salvo automaticamente');
  }

  /**
   * envio para o Backend.
   */
  function criarNota() {
    // Trava de segurança: não deixa mandar nota vazia nem clicar duas vezes seguidas (isPending).
    if (!texto.trim() || criar.isPending) return;
    
    // Envia o documento estruturado sem tags HTML (contrato aceito pelo backend).
    criar.mutate({ titulo, conteudo: texto, categoriaId: categoria.id }, {
      // Se o backend devolver HTTP 201 (Sucesso):
      onSuccess: () => {
        setTitulo(''); // Limpa o campo título na tela
        setTexto('');  // Limpa o campo de texto na tela
        setCategoria(CAT_GENERICO);
        limparRascunho(); // Limpa o localStorage
        flashHint('nota criada ✓', 2500); // Mostra sucesso
      },
      // Se a internet cair ou o backend der erro 500:
      onError: () => flashHint('erro ao criar', 3000), 
    });
  }

  /**
   * Acessibilidade e Agilidade (Atalhos de Teclado)
   */
  function aoTeclar(e) {
    // Se o usuário apertar Ctrl + Enter (ou Command + Enter no Mac), salva a nota!
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') { 
      e.preventDefault(); 
      criarNota(); 
    }
  }

  return (
    <div className="notepad">
      {/* Cabeçalho com o ícone e a mensagem que pisca */}
      <div className="notepad__head">
        <span className="notepad__label"><Ic d={ICONS.notes} />Nova anotação</span>
        <span className={`notepad__hint ${hint ? 'is-visible' : ''}`}>{hint || 'salvo automaticamente'}</span>
      </div>
      
      {/* Input de linha única para o Título */}
      <input
        className="notepad__title"
        type="text"
        maxLength={255} 
        placeholder="Título (opcional)"
        value={titulo}
        onChange={aoDigitarTitulo}
        onKeyDown={aoTeclar}
      />
      
      {/* Área de texto multi-linhas para a nota */}
      <RichTextEditor
        placeholder="Escreva uma anotação…"
        value={texto}
        onChange={(valor) => aoDigitar({ target: { value: valor } })}
        onKeyDown={aoTeclar}
      />

      <div className="notepad__category">
        <span className="notepad__category-label">Categoria</span>
        <CategorySelect categorias={categorias} valor={categoria.nome} onChange={setCategoria} />
      </div>
      
      {/* Rodapé com botão de criar */}
      <div className="notepad__foot">
        <span className="notepad__foot-tip">Ctrl + Enter para criar</span>
        <button
          className="btn btn--primary btn--sm"
          type="button"
          // O botão fica cinza (desativado) se o texto estiver vazio ou se já estiver salvando
          disabled={!texto.trim() || criar.isPending}
          onClick={criarNota}
        >
          Criar nota
        </button>
      </div>
    </div>
  );
}
