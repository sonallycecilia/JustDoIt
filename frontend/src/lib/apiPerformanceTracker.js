// src/lib/apiPerformanceTracker.js

export class ApiPerformanceTracker {
  constructor() {
    this.latencies = [];
  }

  // Registra a latência de uma requisição em milissegundos
  record(latencyMs) {
    if (typeof latencyMs === 'number' && latencyMs >= 0) {
      this.latencies.push(latencyMs);
    }
  }

  // Calcula o P95 (Percentil 95)
  calculateP95() {
    if (this.latencies.length === 0) return 0;

    // Ordena as latências em ordem crescente
    const sorted = [...this.latencies].sort((a, b) => a - b);
    
    // Índice do percentil 95
    const index = Math.floor(sorted.length * 0.95);
    
    return sorted[index] !== undefined ? sorted[index] : sorted[sorted.length - 1];
  }

  clear() {
    this.latencies = [];
  }
}

export const apiTracker = new ApiPerformanceTracker();