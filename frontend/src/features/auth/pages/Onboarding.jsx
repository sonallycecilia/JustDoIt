// Setup guiado pós-cadastro, em 1 passo: primeira categoria + primeira tarefa.
// Ambas são criadas de verdade no backend (POST /categories, POST /tasks).
import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { Mark } from '@/components/Ic';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { CORES_CATEGORIA } from '@/lib/cores';

export default function Onboarding() {
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [categoria, setCategoria] = useState('');
  const [tarefa, setTarefa] = useState('');
  const [cor, setCor] = useState(CORES_CATEGORIA[0]);
  const [erro, setErro] = useState('');

  const concluir = useMutation({
    mutationFn: async ({ nomeCategoria, tituloTarefa, corCategoria }) => {
      const cat = await api.post(endpoints.categories.create, { name: nomeCategoria, color: corCategoria });

      // A resposta pode não trazer o id (ex.: 201 só com Location); nesse caso
      // busca na lista pela categoria recém-criada para obter o UUID real.
      let categoryId = cat?.id ?? null;
      if (!categoryId) {
        const cats = await api.get(endpoints.categories.list);
        categoryId = (Array.isArray(cats) ? cats : []).find((c) => c.name === nomeCategoria)?.id ?? null;
      }

      return api.post(endpoints.tasks.create, {
        title: tituloTarefa,
        description: null,
        categoryId,
        priority: null,
        dueDate: null,
        dueTime: null,
      });
    },
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['categorias'] });
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      navigate('/visao-geral', { replace: true });
    },
    onError: (e) => setErro(e.message || 'Não foi possível concluir. Tente novamente.'),
  });

  const preenchido = categoria.trim() !== '' && tarefa.trim() !== '';

  function enviar() {
    if (!preenchido || concluir.isPending) return;
    setErro('');
    concluir.mutate({
      nomeCategoria: categoria.trim(),
      tituloTarefa: tarefa.trim(),
      corCategoria: cor,
    });
  }

  return (
    <div className="onb">
      <div className="onb__top">
        <div className="onb__brand">
          <span className="onb__brand-mark"><Mark /></span>
          <span className="onb__brand-word">JustDoIt</span>
        </div>
        <Link className="onb__skip" to="/visao-geral">Pular configuração</Link>
      </div>

      <div className="onb__steps">
        <span className="onb__step is-active" />
      </div>

      <div className="onb__card card card--pad">
        <div className="onb__panel is-active">
          <h1 className="onb__title">Crie sua primeira tarefa</h1>
          <p className="onb__sub">Comece com uma categoria e uma tarefa — você pode adicionar mais depois.</p>

          <div className="onb-field">
            <label className="onb-field__label" htmlFor="catName">Nome da categoria</label>
            <input
              id="catName"
              className="onb-input"
              placeholder="Ex.: Faculdade"
              value={categoria}
              onChange={(e) => setCategoria(e.target.value)}
            />
          </div>

          <div className="onb-field">
            <label className="onb-field__label">Cor</label>
            <div className="color-picker">
              {CORES_CATEGORIA.map((c) => (
                <span
                  key={c}
                  className={`color-swatch ${c === cor ? 'is-sel' : ''}`}
                  style={{ background: c }}
                  role="button"
                  tabIndex={0}
                  aria-label="Selecionar cor"
                  onClick={() => setCor(c)}
                />
              ))}
            </div>
          </div>

          <div className="onb-field">
            <label className="onb-field__label" htmlFor="taskName">Primeira tarefa</label>
            <input
              id="taskName"
              className="onb-input"
              placeholder="O que você precisa fazer?"
              value={tarefa}
              onChange={(e) => setTarefa(e.target.value)}
              onKeyDown={(e) => { if (e.key === 'Enter') enviar(); }}
            />
          </div>
        </div>

        {erro && <p className="onb__error" role="alert">{erro}</p>}

        <div className="onb__nav">
          <button
            className="btn btn--primary btn--md"
            type="button"
            disabled={!preenchido || concluir.isPending}
            onClick={enviar}
          >
            {concluir.isPending ? 'Salvando…' : 'Concluir'}
          </button>
        </div>
      </div>
    </div>
  );
}
