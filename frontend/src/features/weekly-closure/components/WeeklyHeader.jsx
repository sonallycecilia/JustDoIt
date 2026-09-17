// src/features/weekly-closure/components/WeeklyHeader.jsx
import React from 'react';

// Se o hook for instanciado no componente pai (Todo.jsx), 
// você deve passar a função via props, ou instanciar o hook aqui.
// Supondo que você passe via props para manter o estado centralizado:
export const WeeklyHeader = ({ currentCycle, onOpenClosure }) => {
  return (
    <div className="flex justify-between items-center mb-4">
      <h2 className="text-xl font-bold">Planejamento da Semana</h2>
      
      {currentCycle?.status === 'OPEN' && (
        <button 
          onClick={onOpenClosure} // Chama a função sem precisar do ID
          className="btn btn-outline btn-sm"
        >
          Encerrar Semana
        </button>
      )}
    </div>
  );
};