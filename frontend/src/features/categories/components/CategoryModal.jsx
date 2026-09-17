import { useEffect, useRef, useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';
import { CORES_CATEGORIA as CORES } from '@/lib/cores';
import { CAT_GENERICO, useAtualizarCategoria, useCriarCategoria } from '@/features/categories/hooks/useCategories';
import { useModalA11y } from '@/hooks/useModalA11y';

// Janela de categoria — cria (POST /categories) ou edita (PUT /categories/{id})
// no backend e invalida as queries ao concluir. Passar `categoria` liga o modo
// de edição; sem ela, o modal cria uma nova.
export default function CategoryModal({ aberto, categoria, onFechar }) {
  const editando = Boolean(categoria);
  const [nome, setNome] = useState('');
  const [cor, setCor] = useState(CORES[0]);
  const [erro, setErro] = useState('');
  const inputRef = useRef(null);
  const dialogRef = useRef(null);

  const criar = useCriarCategoria();
  const atualizar = useAtualizarCategoria();
  const mutation = editando ? atualizar : criar;

  useEffect(() => {
    if (!aberto) return;
    setNome(categoria?.nome || '');
    // Cor atual da categoria; se ela não estiver na paleta, cai na primeira.
    setCor(CORES.includes(categoria?.cor) ? categoria.cor : CORES[0]);
    setErro('');
    criar.reset();
    atualizar.reset();
  }, [aberto, categoria]); // eslint-disable-line react-hooks/exhaustive-deps

  useModalA11y({ aberto, containerRef: dialogRef, initialFocusRef: inputRef, onFechar });

  if (!aberto) return null;

  function salvar() {
    const n = nome.trim();
    if (!n) { setErro('Dê um nome à categoria.'); inputRef.current?.focus(); return; }
    if (editando && n === CAT_GENERICO.nome) {
      setErro('Este nome é reservado à categoria padrão.');
      return;
    }
    if (editando && n === categoria.nome && cor === categoria.cor) { onFechar(); return; }

    setErro('');
    const dados = editando ? { id: categoria.id, nome: n, cor } : { nome: n, cor };
    mutation.mutate(dados, {
      onSuccess: onFechar,
      onError: (e) => setErro(e.message || 'Não foi possível salvar a categoria.'),
    });
  }

  return (
    <div className="cat-modal">
      <div className="cat-modal__backdrop" onClick={onFechar} />
      <div ref={dialogRef} className="cat-modal__card" role="dialog" aria-modal="true" aria-labelledby="category-modal-title" tabIndex={-1}>
        <div className="cat-modal__head">
          <h3 id="category-modal-title" className="cat-modal__title">{editando ? 'Editar categoria' : 'Nova categoria'}</h3>
          <button className="cat-modal__close" type="button" onClick={onFechar} aria-label="Fechar">
            <Ic d={ICONS.close} />
          </button>
        </div>
        <span className="cat-modal__label">Cor</span>
        <div className="cat-modal__colors">
          {CORES.map((c) => (
            <span
              key={c}
              className={`cat-modal__swatch ${c === cor ? 'is-sel' : ''}`}
              style={{ background: c }}
              role="button"
              tabIndex={0}
              aria-label="Selecionar cor"
              onClick={() => setCor(c)}
              onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); setCor(c); } }}
            />
          ))}
        </div>
        <input
          ref={inputRef}
          className="cat-modal__input"
          type="text"
          placeholder="Nome da categoria…"
          autoComplete="off"
          maxLength={40}
          value={nome}
          onChange={(e) => setNome(e.target.value)}
          onKeyDown={(e) => { if (e.key === 'Enter') salvar(); }}
        />
        {erro && <div className="cat-modal__error" role="alert">{erro}</div>}
        <div className="cat-modal__actions">
          <button className="btn btn--secondary btn--sm" type="button" onClick={onFechar}>Cancelar</button>
          <button className="btn btn--primary btn--sm" type="button" onClick={salvar} disabled={mutation.isPending}>
            {mutation.isPending ? 'Salvando…' : (editando ? 'Salvar' : 'Adicionar')}
          </button>
        </div>
      </div>
    </div>
  );
}
