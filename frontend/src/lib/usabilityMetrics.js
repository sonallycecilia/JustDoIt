// src/lib/usabilityMetrics.js

// Chaves para o armazenamento local de telemetria
const FLOW_STORAGE_KEY = 'jdi_usability_flows';

export const UsabilityTracker = {
  // Inicia um fluxo (ex: 'criar-tarefa', 'cadastrar-categoria')
  iniciarFluxo(flowName) {
    const flows = JSON.parse(localStorage.getItem(FLOW_STORAGE_KEY) || '{}');
    flows[flowName] = {
      status: 'iniciado',
      startTime: Date.now(),
      ajudaSolicitada: false
    };
    localStorage.setItem(FLOW_STORAGE_KEY, JSON.stringify(flows));
  },

  // Marca que o usuário pediu ajuda (documentação, tooltip de erro excessivo, etc)
  registrarAjuda(flowName) {
    const flows = JSON.parse(localStorage.getItem(FLOW_STORAGE_KEY) || '{}');
    if (flows[flowName]) {
      flows[flowName].ajudaSolicitada = true;
      localStorage.setItem(FLOW_STORAGE_KEY, JSON.stringify(flows));
    }
  },

  // Conclui o fluxo com sucesso
  concluirFluxo(flowName) {
    const flows = JSON.parse(localStorage.getItem(FLOW_STORAGE_KEY) || '{}');
    if (flows[flowName]) {
      flows[flowName].status = 'concluido';
      localStorage.setItem(FLOW_STORAGE_KEY, JSON.stringify(flows));
    }
  },

  // Calcula a métrica TC (Taxa de Conclusão sem ajuda) em porcentagem
  calcularTaxaConclusao() {
    const flows = JSON.parse(localStorage.getItem(FLOW_STORAGE_KEY) || '{}');
    const entries = Object.values(flows);
    
    if (entries.length === 0) return 0;

    const totalParticipantes = entries.length;
    const concluidosSemAjuda = entries.filter(
      (f) => f.status === 'concluido' && !f.ajudaSolicitada
    ).length;

    return (concluidosSemAjuda / totalParticipantes) * 100;
  },

  limparMetricas() {
    localStorage.removeItem(FLOW_STORAGE_KEY);
  }
};