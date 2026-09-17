// A estimativa da tarefa NÃO dimensiona o evento virtual da grade. Antes ela
// dimensionava: uma tarefa de 3h ocupava três faixas do dia, então preencher a
// estimativa (que é o que faz o card "Tempo executado" da Visão Geral sair do
// zero) tinha o efeito colateral de entupir o calendário.
import { describe, expect, it, vi } from 'vitest';

// A cadeia de import do WeeklyCalendar puxa os hooks que falam com o backend.
// Nenhuma chamada acontece aqui — os mocks só evitam carregar os clientes reais.
vi.mock('@/api/client', () => ({ api: {}, getOuNull: vi.fn() }));

const { enriquecerComTarefa, tarefasComoEventos } = await import('@/features/calendar/components/WeeklyCalendar');

const porDia = () => ({ d: 0 });

function tarefa(extra) {
  return { id: 't1', titulo: 'Estudar cálculo', dataIso: '2026-08-05', hora: '08:30', ...extra };
}

describe('tarefasComoEventos — altura do evento virtual', () => {
  it('dá uma hora de grade à tarefa, qualquer que seja a estimativa', () => {
    const [ev] = tarefasComoEventos([], [tarefa({ duracaoMin: 180 })], porDia);

    expect(ev.ini).toBe(8.5);
    expect(ev.fim).toBe(9.5);
  });

  it('usa a mesma altura para uma tarefa sem estimativa nenhuma', () => {
    const [comEstimativa] = tarefasComoEventos([], [tarefa({ duracaoMin: 180 })], porDia);
    const [semEstimativa] = tarefasComoEventos([], [tarefa({ duracaoMin: null })], porDia);

    expect(semEstimativa.fim - semEstimativa.ini).toBe(comEstimativa.fim - comEstimativa.ini);
  });

  it('leva a estimativa para o bloco exibir como texto', () => {
    const [ev] = tarefasComoEventos([], [tarefa({ duracaoMin: 180 })], porDia);

    expect(ev.estimadoMin).toBe(180);
  });

  it('deixa estimadoMin nulo quando a tarefa não tem estimativa', () => {
    const [ev] = tarefasComoEventos([], [tarefa({ duracaoMin: null })], porDia);

    expect(ev.estimadoMin).toBeNull();
  });

  it('não duplica a tarefa que já tem bloco de tempo real na grade', () => {
    const eventos = tarefasComoEventos([{ taskId: 't1' }], [tarefa({ duracaoMin: 180 })], porDia);

    expect(eventos).toHaveLength(0);
  });
});

describe('blocos vinculados a tarefas removidas', () => {
  it('descarta o bloco órfão em vez de oferecer uma conclusão que sempre retorna 404', () => {
    const bloco = { id: 'bloco-1', taskId: 'tarefa-removida', iso: '2026-08-05', ini: 8, fim: 9 };

    expect(enriquecerComTarefa(bloco, new Map())).toBeNull();
  });

  it('preserva blocos avulsos, que não referenciam uma tarefa', () => {
    const bloco = { id: 'bloco-1', taskId: null, iso: '2026-08-05', ini: 8, fim: 9 };

    expect(enriquecerComTarefa(bloco, new Map())).toMatchObject({
      id: 'bloco-1',
      taskId: null,
      titulo: 'Bloco',
    });
  });
});
