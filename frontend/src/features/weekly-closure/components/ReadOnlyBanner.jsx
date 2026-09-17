// src/features/weekly-closure/components/ReadOnlyBanner.jsx
import React from 'react';

// Mesma lógica, receba a ação por props
export const ReadOnlyBanner = ({ cycle, onOpenClosure }) => {
  if (cycle?.status === 'CLOSED') {
    return (
      <div className="bg-gray-200 text-gray-700 p-2 text-center">
        Modo de Leitura: Este ciclo já foi encerrado e consolidado.
      </div>
    );
  }

  if (cycle?.status === 'PENDING_REVIEW') {
    return (
      <div className="bg-yellow-100 text-yellow-800 p-3 flex justify-between items-center">
        <span>O tempo deste ciclo acabou! Faça o fechamento para iniciar a nova semana.</span>
        <button 
          onClick={onOpenClosure} // Chama a função sem precisar do ID
          className="bg-yellow-500 text-white px-4 py-1 rounded"
        >
          Fazer Triagem Agora
        </button>
      </div>
    );
  }

  return null;
};