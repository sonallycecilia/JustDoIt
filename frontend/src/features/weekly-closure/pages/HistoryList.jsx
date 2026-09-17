import React from 'react';
import { Link } from 'react-router-dom';
import Sidebar from '@/components/Sidebar';
// Suponha que você crie esse hook para buscar a lista de ciclos fechados
import { useClosedCycles } from '@/features/weekly-closure/hooks/useClosedCycles'; 

export function HistoryList() {
  const { data: cycles, isLoading, isError, refetch } = useClosedCycles();

  return (
    <div className="app">
      <Sidebar ativa="history" />
      <main className="app__main">
        <div className="page">
          <header className="page__head">
            <h1 className="page__title">Histórico de Semanas</h1>
            <div className="page__eyebrow">Ciclos encerrados e consolidados</div>
          </header>

          {isLoading ? (
            <p>Carregando histórico...</p>
          ) : isError ? (
            // Antes: só existia o ramo isLoading, então um erro de rede fazia
            // essa tela ficar presa em "Carregando..." até o React Query
            // desistir das retentativas, sem nunca avisar o usuário.
            <div className="mt-6">
              <p className="text-slate-400">Não foi possível carregar o histórico de semanas.</p>
              <button
                type="button"
                className="mt-2 text-indigo-400 underline"
                onClick={() => refetch()}
              >
                Tentar novamente
              </button>
            </div>
          ) : !cycles?.length ? (
            <p className="text-slate-400 mt-6">Nenhuma semana encerrada ainda.</p>
          ) : (
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 mt-6">
              {cycles.map(cycle => (
                <Link 
                  key={cycle.id} 
                  to={`/history/${cycle.id}`}
                  className="p-4 bg-slate-800 border border-slate-700 rounded-lg hover:border-indigo-500 transition-colors"
                >
                  <h3 className="font-bold text-white">
                    Semana de {new Date(cycle.startDate).toLocaleDateString()} a {new Date(cycle.endDate).toLocaleDateString()}
                  </h3>
                  <p className="text-sm text-slate-400 mt-2">Clique para ver o balanço completo.</p>
                </Link>
              ))}
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
