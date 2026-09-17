import { describe, it, expect, beforeEach } from 'vitest';
import { UsabilityTracker } from '@/lib/usabilityMetrics';

describe('Métrica de Usabilidade: Taxa de Conclusão de Tarefas (TC)', () => {
  beforeEach(() => {
    UsabilityTracker.limparMetricas();
  });

  it('deve calcular a taxa de conclusão sem ajuda corretamente e atingir a meta >= 90%', () => {
    // Simular 10 usuários executando o fluxo principal ('criar-tarefa')
    
    // 9 usuários concluem com sucesso e SEM ajuda
    for (let i = 0; i < 9; i++) {
      UsabilityTracker.iniciarFluxo(`fluxo-${i}`);
      UsabilityTracker.concluirFluxo(`fluxo-${i}`);
    }

    // 1 usuário conclui, mas solicitou ajuda
    UsabilityTracker.iniciarFluxo('fluxo-com-ajuda');
    UsabilityTracker.registrarAjuda('fluxo-com-ajuda');
    UsabilityTracker.concluirFluxo('fluxo-com-ajuda');

    const taxaConclusao = UsabilityTracker.calcularTaxaConclusao();

    // 9 de 10 = 90%
    console.log(`[Usabilidade] Taxa de Conclusão calculada: ${taxaConclusao}%`);
    
    expect(taxaConclusao).toBeGreaterThanOrEqual(90);
  });
});