import { useState } from 'react';
import Ic, { ICONS } from '@/components/Ic';

// Dropdown de categoria — a mesma caixa usada nos filtros do To Do e na
// escolha de categoria do drawer do calendário. Estilos (.cat-filter*) vivem
// em styles/pages/todo.css, importado globalmente no main.jsx.
//
// `valor` é o NOME da categoria selecionada (ou null/'all' quando `incluirTodas`
// está ligado e nada está filtrado). `onChange` recebe o objeto da categoria,
// ou null para a opção "Todas as categorias".
export default function CategorySelect({
  categorias,
  valor,
  onChange,
  incluirTodas = false,
  rotuloTodas = 'Todas as categorias',
  desabilitado = false,
}) {
  const [aberto, setAberto] = useState(false);

  const selecionada = (categorias || []).find((c) => c.nome === valor);
  const semOpcoes = (categorias || []).length === 0 && !incluirTodas;
  const rotulo = selecionada
    ? selecionada.nome
    : (incluirTodas ? rotuloTodas : 'Sem categorias');

  function escolher(c) {
    setAberto(false);
    onChange(c);
  }

  return (
    <div className="cat-filter">
      <button
        className={`cat-filter__btn ${aberto ? 'is-open' : ''}`}
        type="button"
        aria-haspopup="listbox"
        aria-expanded={aberto}
        disabled={desabilitado || semOpcoes}
        onClick={() => !semOpcoes && setAberto((v) => !v)}
      >
        {selecionada && <span className="cat-filter__dot" style={{ background: selecionada.cor }} />}
        <span className="cat-filter__name">{rotulo}</span>
        <Ic d={ICONS.chevron} className="cat-filter__chevron" size={13} strokeWidth={2} />
      </button>

      {aberto && (
        <>
          <div className="cat-filter__overlay" onClick={() => setAberto(false)} />
          <div className="cat-filter__menu" role="listbox">
            {incluirTodas && (
              <button
                className={`cat-filter__item ${!selecionada ? 'is-on' : ''}`}
                type="button"
                role="option"
                aria-selected={!selecionada}
                onClick={() => escolher(null)}
              >
                <span className="cat-filter__item-name">{rotuloTodas}</span>
                {!selecionada && <span className="cat-filter__check"><Ic d={ICONS.check} /></span>}
              </button>
            )}
            {(categorias || []).map((c) => {
              const ativa = c.nome === valor;
              return (
                <button
                  key={c.id}
                  className={`cat-filter__item ${ativa ? 'is-on' : ''}`}
                  type="button"
                  role="option"
                  aria-selected={ativa}
                  onClick={() => escolher(c)}
                >
                  <span className="cat-filter__dot" style={{ background: c.cor }} />
                  <span className="cat-filter__item-name">{c.nome}</span>
                  {ativa && <span className="cat-filter__check"><Ic d={ICONS.check} /></span>}
                </button>
              );
            })}
          </div>
        </>
      )}
    </div>
  );
}
