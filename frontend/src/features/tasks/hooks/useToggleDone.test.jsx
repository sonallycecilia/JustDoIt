// Concluir a tarefa tem de virar tempo executado no BACKEND, senão quem organiza
// o dia pela lista (sem Pomodoro e sem cronômetro) termina a semana com "0h
// executadas" no dashboard mesmo tendo concluído tudo.
//
// O que este teste protege: a estimativa só é registrada quando não há tempo
// medido, e nunca duas vezes.
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, getOuNull } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { useToggleDone } from '@/features/tasks/hooks/useTasks';

vi.mock('@/api/client', async (original) => ({
  ...(await original()),
  api: { get: vi.fn(), patch: vi.fn() },
  // O timer é lido com getOuNull (404 = tarefa ainda sem timer), então é ele que
  // o teste controla, não o api.get de dentro dele.
  getOuNull: vi.fn(),
}));

const ID = 'tarefa-1';

function criarWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } });
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

// GET /tasks/{id}/timer devolve estimativa e acumulado da tarefa.
function timerDoServidor({ estimatedMinutes = null, actualSeconds = 0 } = {}) {
  getOuNull.mockResolvedValue({ estimatedMinutes, actualSeconds });
}

function logsDeTempo() {
  return api.patch.mock.calls.filter(([url]) => url === endpoints.tasks.timerLog(ID));
}

async function alternar(concluir) {
  const { result } = renderHook(() => useToggleDone(), { wrapper: criarWrapper() });
  result.current.mutate({ id: ID, concluir });
  await waitFor(() => expect(result.current.isSuccess).toBe(true));
}

describe('concluir tarefa → tempo executado', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.patch.mockResolvedValue({});
  });

  it('registra a estimativa como tempo executado ao concluir', async () => {
    timerDoServidor({ estimatedMinutes: 90 });

    await alternar(true);

    expect(api.patch).toHaveBeenCalledWith(endpoints.tasks.complete(ID));
    expect(logsDeTempo()).toEqual([[endpoints.tasks.timerLog(ID), {
      seconds: 5400,
      source: 'COMPLETION_ESTIMATE',
    }]]);
  });

  it('não sobrescreve tempo que foi medido de verdade', async () => {
    timerDoServidor({ estimatedMinutes: 90, actualSeconds: 120 });

    await alternar(true);

    expect(logsDeTempo()).toHaveLength(0);
  });

  it('não inventa tempo para tarefa sem estimativa', async () => {
    timerDoServidor({ estimatedMinutes: null });

    await alternar(true);

    expect(logsDeTempo()).toHaveLength(0);
  });

  it('não registra tempo ao reabrir a tarefa', async () => {
    timerDoServidor({ estimatedMinutes: 90 });

    await alternar(false);

    expect(api.patch).toHaveBeenCalledWith(endpoints.tasks.reopen(ID));
    expect(logsDeTempo()).toHaveLength(0);
  });

  it('mantém a tarefa concluída mesmo se o registro de tempo falhar', async () => {
    timerDoServidor({ estimatedMinutes: 90 });
    api.patch.mockImplementation((url) => (url === endpoints.tasks.timerLog(ID)
      ? Promise.reject(new Error('timer fora do ar'))
      : Promise.resolve({})));

    // O sucesso da mutation é o que segura o check na tela: se o log de tempo
    // derrubasse a conclusão, a UI reverteria algo que o backend já gravou.
    await alternar(true);
  });
});
