import { useMemo, useState, useEffect } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { List } from 'react-window';

import Ic, { ICONS } from '@/components/Ic';
import Sidebar from '@/components/Sidebar';
import CategorySelect from '@/features/categories/components/CategorySelect';
import NoteComposer from '@/features/notes/components/NoteComposer';
import RecurringDeleteModal from '@/features/tasks/components/RecurringDeleteModal';
import { useCategorias } from '@/features/categories/hooks/useCategories';
import { useRemoverTarefa, useTarefas, useToggleDone } from '@/features/tasks/hooks/useTasks';
import * as Priority from '@/features/tasks/lib/priority';
import { useWeeklyClosure } from '@/features/weekly-closure/hooks/useWeeklyClosure';
import { ClosurePreviewModal } from '@/features/weekly-closure/components/ClosurePreviewModal';
import { ReadOnlyBanner } from '@/features/weekly-closure/components/ReadOnlyBanner';

const STATUS = [
  { valor: 'open', rotulo: 'Abertas' },
  { valor: 'done', rotulo: 'Concluídas' },
  { valor: 'all', rotulo: 'Todas' },
];
const DATAS = [
  { valor: 'all', rotulo: 'Qualquer data' },
  { valor: 'today', rotulo: 'Hoje' },
  { valor: 'week', rotulo: 'Esta semana' },
];

export function selecionarOcorrenciasPertinentes(tarefas) {
  return tarefas.filter((tarefa) => !tarefa.seriesId);
}

const TaskRow = ({
  index,
  style,
  ariaAttributes,
  items,
  isReadOnly,
  toggleDone,
  navigate,
  remover,
  setErroExclusao,
  setExcluindo,
}) => {
  const item = items[index];

  if (item.type === 'header') {
    return (
      <div {...ariaAttributes} style={{ ...style, paddingTop: '12px' }} className="prio-group">
        <div className="prio-group__head">
          <span className="prio-group__bar" style={{ background: item.cor }} />
          <span className="prio-group__title">{item.rotulo}</span>
          <span className="prio-group__n">{item.itens.length}</span>
        </div>
      </div>
    );
  }

  const t = item;
  return (
    <div {...ariaAttributes} style={{ ...style, paddingBottom: '8px' }}>
      <div className="todo-list" style={{ margin: 0, padding: 0 }}>
        <div className={`todo-item ${t.done ? 'is-done' : ''}`} style={{ margin: 0 }}>
          <button
            className="todo-check"
            aria-label="Concluir"
            disabled={isReadOnly}
            onClick={() => toggleDone.mutate({ id: t.id, concluir: !t.done })}
          >
            <Ic d={ICONS.check} />
          </button>
          <div className="todo-main" onClick={() => navigate(`/tasks/${t.id}`)}>
            <div className="todo-title">{t.titulo}</div>
            <div className="todo-meta">
              <span className="todo-cat">
                <span className="todo-cat__dot" style={{ background: t.catCor }} />
                {t.cat}
              </span>
              <span className={`todo-date ${t.overdue ? 'is-overdue' : ''}`}>
                <Ic d={ICONS.calendar} />{t.data}
              </span>
              {t.hora && <span className="todo-time"><Ic d={ICONS.clock} />{t.hora}</span>}
            </div>
          </div>
          
          <div className="todo-right">
            <span className={`badge badge--${Priority.normalizar(t.prioridade)}`}>
              {Priority.ROTULO[Priority.normalizar(t.prioridade)]}
            </span>
            
            {!isReadOnly && (
              <button
                className="todo-del"
                aria-label="Excluir tarefa"
                title="Excluir tarefa"
                disabled={remover.isPending}
                onClick={(e) => {
                  e.stopPropagation();
                  if (t.seriesId || t.cycleType) {
                    setErroExclusao('');
                    setExcluindo(t);
                  } else {
                    remover.mutate({ id: t.id });
                  }
                }}
              >
                <Ic d={ICONS.trash} />
              </button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default function Todo() {
  const navigate = useNavigate();
  const [filtros, setFiltros] = useState({ cat: 'all', status: 'open', date: 'all' });
  const [excluindo, setExcluindo] = useState(null);
  const [erroExclusao, setErroExclusao] = useState('');

  const { data: categorias } = useCategorias();
  const { data: tarefas, isLoading, isError, refetch } = useTarefas(categorias);
  const toggleDone = useToggleDone();
  const remover = useRemoverTarefa();

  const { 
      previewData, 
      isModalOpen, 
      setIsModalOpen, 
      loadPreview, 
      handleSubmittingClosure,
      isLoading: isClosureLoading 
  } = useWeeklyClosure(() => {
    refetch();
  });

  const dynamicCycleStatus = useMemo(() => {
    if (previewData && (previewData.pendingTasks?.length > 0 || previewData.completedTasks?.length > 0)) {
      return 'PENDING_REVIEW';
    }
    return 'OPEN';
  }, [previewData]);

  const isReadOnly = dynamicCycleStatus === 'PENDING_REVIEW' || dynamicCycleStatus === 'CLOSED';

  const visiveis = useMemo(() => {
    const passa = (t) => {
      if (filtros.cat !== 'all' && t.cat !== filtros.cat) return false;
      if (filtros.status === 'open' && t.done) return false;
      if (filtros.status === 'done' && !t.done) return false;
      if (filtros.date === 'today' && !['today', 'past'].includes(t.quando)) return false;
      if (filtros.date === 'week' && !['past', 'today', 'week'].includes(t.quando)) return false;
      return true;
    };
    return selecionarOcorrenciasPertinentes((tarefas || []).filter(passa)).sort(Priority.comparar);
  }, [tarefas, filtros]);

  const grupos = useMemo(() => Priority.agrupar(visiveis), [visiveis]);

  const flattenedData = useMemo(() => {
    const flat = [];
    grupos.forEach((g) => {
      flat.push({ type: 'header', ...g });
      g.itens.forEach((t) => {
        flat.push({ type: 'task', ...t });
      });
    });
    return flat;
  }, [grupos]);

  const listData = useMemo(() => ({
    items: flattenedData,
    isReadOnly,
    toggleDone,
    navigate,
    remover,
    setErroExclusao,
    setExcluindo
  }), [flattenedData, isReadOnly, toggleDone, navigate, remover]);

  const medirFiltro = (novoFiltro) => {
    performance.mark('filtro-click');
    setFiltros(novoFiltro);
  };

  useEffect(() => {
    if (!performance.getEntriesByName('filtro-click').length) return;
    performance.mark('filtro-render');
    const { duration } = performance.measure('filtro-latencia', 'filtro-click', 'filtro-render');
    performance.clearMarks('filtro-click');
    performance.clearMarks('filtro-render');
    if (duration > 500) {
      console.warn(`[perf] filtro levou ${duration.toFixed(0)}ms (SLA: 500ms)`);
    }
  }, [visiveis]);

  return (
    <div className="app">
      <Sidebar ativa="todo" />

      <main className="app__main">
        <div className="page">
          
          <ReadOnlyBanner 
            cycle={{ status: dynamicCycleStatus }} 
            onOpenClosure={loadPreview} 
          />

          <header className="page__head">
            <div>
              <h1 className="page__title">To Do</h1>
              <div className="page__eyebrow">Todas as tarefas</div>
            </div>
          </header>

          <div className="page__head-actions">
            {dynamicCycleStatus === 'OPEN' && (
              <button 
                className="btn btn--secondary btn--md" 
                style={{ marginRight: '8px' }}
                onClick={loadPreview}
                disabled={isClosureLoading}
              >
                {isClosureLoading ? 'Carregando...' : 'Encerrar Semana'}
              </button>
            )}
            <Link className="btn btn--primary btn--md" to="/tasks/nova">+ Nova tarefa</Link>
          </div>

          <NoteComposer />

          <div className="filters">
            <CategorySelect
              categorias={categorias}
              valor={filtros.cat}
              onChange={(c) => medirFiltro((f) => ({ ...f, cat: c ? c.nome : 'all' }))}
              incluirTodas
            />

            <div className="filter-group">
              {STATUS.map((s) => (
                <button
                  key={s.valor}
                  className={`filter-chip ${filtros.status === s.valor ? 'is-active' : ''}`}
                  onClick={() => medirFiltro((f) => ({ ...f, status: s.valor }))}
                >
                  {s.rotulo}
                </button>
              ))}
            </div>
            <div className="filter-group">
              {DATAS.map((d) => (
                <button
                  key={d.valor}
                  className={`filter-chip ${filtros.date === d.valor ? 'is-active' : ''}`}
                  onClick={() => medirFiltro((f) => ({ ...f, date: d.valor }))}
                >
                  {d.rotulo}
                </button>
              ))}
            </div>
            <div className="filters__spacer" />
            <span className="filters__count">{visiveis.length} {visiveis.length === 1 ? 'tarefa' : 'tarefas'}</span>
          </div>

          {isLoading && <div className="empty"><p>Carregando tarefas…</p></div>}

          {isError && (
            <div className="empty">
              <p>Sem conexão com o servidor — não foi possível carregar suas tarefas.</p>
              <button className="btn btn--secondary btn--md" onClick={() => refetch()}>Tentar de novo</button>
            </div>
          )}

          {!isLoading && !isError && visiveis.length === 0 && (
            <div className="empty">
              <Ic d={ICONS.checkCircle} />
              <p>Nada por aqui — aproveite.</p>
            </div>
          )}

          {!isLoading && !isError && visiveis.length > 0 && (
            <div style={{ height: '600px', width: '100%', marginBottom: '32px' }}>
              <List
                key={JSON.stringify(filtros)}
                style={{ height: 600, width: '100%' }}
                rowCount={flattenedData.length}
                rowHeight={(index) => flattenedData[index].type === 'header' ? 50 : 85}
                rowProps={listData}
                rowComponent={TaskRow}
                overscanCount={5}
              />
            </div>
          )}

        </div>
      </main>

      <RecurringDeleteModal
        tarefa={excluindo}
        processando={remover.isPending}
        erro={erroExclusao}
        onFechar={() => !excluindo && !remover.isPending && setExcluindo(null)}
        onEscolher={(scope) => remover.mutate({
          id: excluindo.id,
          scope,
          seriesId: excluindo.seriesId,
        }, {
          onSuccess: () => setExcluindo(null),
          onError: (e) => setErroExclusao(e.message || 'Não foi possível excluir a tarefa.'),
        })}
      />

      <ClosurePreviewModal 
          isOpen={isModalOpen}
          previewData={previewData || { pendingTasks: [], completedTasks: [] }}
          onClose={() => setIsModalOpen(false)}
          onSubmit={handleSubmittingClosure}
      />
    </div>
  );
}
