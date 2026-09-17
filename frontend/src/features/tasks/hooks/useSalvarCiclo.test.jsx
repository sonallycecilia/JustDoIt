import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { endpoints } from '@/api/endpoints';

vi.mock('@/api/client', () => ({
  api: { put: vi.fn(), remove: vi.fn() },
  getOuNull: vi.fn(),
}));

const { api } = await import('@/api/client');
const { useSalvarCiclo } = await import('@/features/tasks/hooks/useTaskDetail');

describe('useSalvarCiclo', () => {
  beforeEach(() => vi.clearAllMocks());

  it('atualiza a lista depois que o backend materializa as ocorrências diárias', async () => {
    api.put.mockResolvedValue({ cycleType: 'DAILY' });
    const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    qc.setQueryData(['tarefas'], [{ id: 'modelo' }]);
    const invalidar = vi.spyOn(qc, 'invalidateQueries');
    const wrapper = ({ children }) => (
      <QueryClientProvider client={qc}>{children}</QueryClientProvider>
    );
    const { result } = renderHook(() => useSalvarCiclo('modelo'), { wrapper });

    act(() => result.current.mutate('DAILY'));

    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(api.put).toHaveBeenCalledWith(
      endpoints.tasks.cycleConfig('modelo'),
      { cycleType: 'DAILY' },
    );
    expect(invalidar).toHaveBeenCalledWith({ queryKey: ['tarefas'] });
  });
});
