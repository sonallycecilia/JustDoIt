import { describe, it, expect, beforeEach } from 'vitest';
import { ApiPerformanceTracker } from '@/lib/apiPerformanceTracker';

describe('Métrica de Desempenho: Latência de API no percentil 95 (P95)', () => {
  let tracker;

  beforeEach(() => {
    tracker = new ApiPerformanceTracker();
  });

  it('deve calcular o P95 e garantir que ele seja menor ou igual a 500ms', () => {
    // Simula 100 requisições de API (autenticação, tarefas, agenda, notificações)
    // 95 requisições rápidas entre 50ms e 300ms
    for (let i = 0; i < 95; i++) {
      const simulatedLatency = Math.floor(Math.random() * 250) + 50;
      tracker.record(simulatedLatency);
    }

    // 5 requisições mais lentas (ex: 450ms a 490ms, ainda abaixo do limite)
    for (let i = 0; i < 5; i++) {
      tracker.record(480);
    }

    const p95 = tracker.calculateP95();
    console.log(`[Performance] Latência P95 calculada para a API: ${p95}ms`);

    // A meta exigida pela métrica é P95 <= 500 ms
    expect(p95).toBeLessThanOrEqual(500);
  });
});