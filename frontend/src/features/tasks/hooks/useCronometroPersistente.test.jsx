import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, getOuNull } from '@/api/client';
import {
  useCronometroAtivo,
  useIniciarCronometro,
  usePararCronometro,
} from '@/features/tasks/hooks/useTaskDetail';

vi.mock('@/api/client', () => ({
  api: {
    post: vi.fn(),
  },
  getOuNull: vi.fn(),
}));

const TASK_ID = '00000000-0000-0000-0000-000000000001';
const STARTED_AT = '2026-07-31T10:00:00';

function contexto() {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return {
    queryClient,
    wrapper: ({ children }) => (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    ),
  };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe('cronômetro persistente', () => {
  it('recupera do backend o cronômetro que continuou ativo após fechar a página', async () => {
    getOuNull.mockResolvedValue({ taskId: TASK_ID, startedAt: STARTED_AT });
    const { wrapper } = contexto();

    const { result } = renderHook(() => useCronometroAtivo(), { wrapper });

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(result.current.data).toEqual({ taskId: TASK_ID, startedAt: STARTED_AT });
  });

  it('inicia no servidor e registra o cronômetro ativo no cache compartilhado', async () => {
    const ativo = { taskId: TASK_ID, startedAt: STARTED_AT };
    api.post.mockResolvedValue(ativo);
    const { wrapper, queryClient } = contexto();
    const { result } = renderHook(() => useIniciarCronometro(TASK_ID), { wrapper });

    await act(() => result.current.mutateAsync());

    expect(api.post).toHaveBeenCalledWith(expect.stringContaining(`/tasks/${TASK_ID}/timer/start`));
    expect(queryClient.getQueryData(['timer-active'])).toEqual(ativo);
  });

  it('para no servidor, remove a sessão ativa e atualiza o total acumulado', async () => {
    api.post.mockResolvedValue({ taskId: TASK_ID, actualSeconds: 367 });
    const { wrapper, queryClient } = contexto();
    queryClient.setQueryData(['timer-active'], { taskId: TASK_ID, startedAt: STARTED_AT });
    queryClient.setQueryData(['timer', TASK_ID], { segundos: 120, estimadoMin: 30 });
    const { result } = renderHook(() => usePararCronometro(TASK_ID), { wrapper });

    await act(() => result.current.mutateAsync());

    expect(api.post).toHaveBeenCalledWith(expect.stringContaining(`/tasks/${TASK_ID}/timer/stop`));
    expect(queryClient.getQueryData(['timer-active'])).toBeNull();
    expect(queryClient.getQueryData(['timer', TASK_ID])).toEqual({
      segundos: 367,
      estimadoMin: 30,
    });
  });
});
