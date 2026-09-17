import React from 'react';
import { useParams } from 'react-router-dom';
import Sidebar from '@/components/Sidebar';
import { ReadOnlyBanner } from '@/features/weekly-closure/components/ReadOnlyBanner';
// Hook para buscar os snapshots de um ciclo específico
import { useCycleSnapshots } from '@/features/weekly-closure/hooks/useCycleSnapshots'; 

export function HistoryDetail() {
  const { cycleId } = useParams();
  const { data: snapshotData, isLoading, isError, refetch } = useCycleSnapshots(cycleId);

  // snapshotData deve conter os detalhes do ciclo e a lista de tarefas congeladas

  return (
    <div className="app">
      <Sidebar ativa="history" />
      <main className="app__main">
        <div className="page">
          
          {/* Banner garantindo o aviso visual de imutabilidade */}
          <ReadOnlyBanner cycle={{ status: 'CLOSED' }} />

          <header className="page__head mt-4">
            <h1 className="page__title">Balanço da Semana</h1>
          </header>

          {isLoading ? (
            <p>Carregando dados da semana...</p>
          ) : isError ? (
            <div className="mt-6">
              <p className="text-slate-400">Não foi possível carregar os dados desta semana.</p>
              <button
                type="button"
                className="mt-2 text-indigo-400 underline"
                onClick={() => refetch()}
              >
                Tentar novamente
              </button>
            </div>
          ) : (
            <div className="mt-6 space-y-2">
              {/* '?.tasks?.' em vez de '?.tasks.': snapshotData pode existir
                  sem 'tasks' preenchido; sem a segunda chave opcional isso
                  quebrava com "Cannot read properties of undefined". */}
              {snapshotData?.tasks?.map(task => (
                <div key={task.id} className="todo-item is-readonly bg-slate-800/50 opacity-80 cursor-not-allowed">
                  {/* Interface da tarefa congelada (sem botões de concluir ou excluir) */}
                  <div className="todo-main">
                    <div className="todo-title text-slate-300">{task.title}</div>
                    <div className="todo-meta text-xs">
                      Status no fechamento: 
                      <span className="ml-1 font-bold">
                        {task.statusAtClosure === 'DONE' ? 'Concluída' : task.statusAtClosure}
                      </span>
                    </div>
                  </div>
                </div>
              ))}
              {!snapshotData?.tasks?.length && (
                <p className="text-slate-400">Nenhuma tarefa registrada nesta semana.</p>
              )}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
