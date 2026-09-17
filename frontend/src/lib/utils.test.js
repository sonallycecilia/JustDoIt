import { describe, expect, it } from 'vitest';
import { intervaloSemana } from '@/lib/utils';

describe('intervaloSemana', () => {
  it('usa segunda a domingo, como o schedule-service', () => {
    const semana = intervaloSemana(new Date(2026, 7, 12)); // quarta-feira

    expect(semana.inicioIso).toBe('2026-08-10');
    expect(semana.fimIso).toBe('2026-08-16');
  });

  it('mantém o domingo na semana que começou na segunda anterior', () => {
    const semana = intervaloSemana(new Date(2026, 7, 16)); // domingo

    expect(semana.inicioIso).toBe('2026-08-10');
    expect(semana.fimIso).toBe('2026-08-16');
  });
});
