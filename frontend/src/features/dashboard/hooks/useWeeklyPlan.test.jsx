// Fechamento da semana. O que este teste protege: 404 tratado como "semana ainda
// não aberta" (e não como erro), o resumo só ser lido depois de fechada, e o
// fechamento abrir o plano quando ele ainda não existe.
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '@/api/client';
import { usePlanoSemanal } from '@/features/dashboard/hooks/useWeeklyPlan';
import { intervaloSemana } from '@/lib/utils';

vi.mock('@/api/client', async (original) => ({
  ...(await original()),
  api: { get: vi.fn(), post: vi.fn(), patch: vi.fn() },
}));

function criarWrapper() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

const semana = intervaloSemana();
const PLAN_ID = 'plano-1';

function naoEncontrado() {
  return Promise.reject(new ApiError('Erro 404', 404));
}

async function montar() {
  const { result } = renderHook(() => usePlanoSemanal(semana), { wrapper: criarWrapper() });
  await waitFor(() => expect(result.current.carregando).toBe(false));
  return result;
}

describe('usePlanoSemanal', () => {
  beforeEach(() => vi.clearAllMocks());

  it('não consulta plano quando a tela está na visão geral', async () => {
    const { result } = renderHook(
      () => usePlanoSemanal(semana, { enabled: false }),
      { wrapper: criarWrapper() },
    );

    expect(result.current.carregando).toBe(false);
    expect(api.get).not.toHaveBeenCalled();
  });

  it('trata 404 do plano como semana ainda não aberta, sem erro', async () => {
    api.get.mockImplementation(naoEncontrado);

    const result = await montar();

    expect(result.current.plano).toBeNull();
    expect(result.current.fechada).toBe(false);
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining(`weekStart=${semana.inicioIso}`));
  });

  it('não busca resumo enquanto a semana está aberta', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('weekStart')) {
        return Promise.resolve({ id: PLAN_ID, status: 'OPEN', weekStartDate: semana.inicioIso });
      }
      return naoEncontrado();
    });

    const result = await montar();

    expect(result.current.fechada).toBe(false);
    expect(result.current.resumo).toBeNull();
    // Com a semana aberta os números são ao vivo: pedir o resumo seria supérfluo
    expect(api.get.mock.calls.some(([url]) => url.includes('/summary'))).toBe(false);
  });

  it('carrega o retrato congelado quando a semana está fechada', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('weekStart')) {
        return Promise.resolve({ id: PLAN_ID, status: 'CLOSED', weekStartDate: semana.inicioIso });
      }
      return Promise.resolve({
        weeklyPlanId: PLAN_ID,
        totalScheduledMinutes: 120,
        totalEstimatedMinutes: 90,
        totalActualSeconds: 3600,
        completedTasks: 3,
        totalTasks: 5,
      });
    });

    const result = await montar();

    expect(result.current.fechada).toBe(true);
    expect(result.current.resumo.totalScheduledMinutes).toBe(120);
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining(`/weekly-plans/${PLAN_ID}/summary`));
  });

  it('abre o plano antes de fechar quando a semana ainda não existe', async () => {
    api.get.mockImplementation(naoEncontrado);
    api.post.mockResolvedValue({ id: PLAN_ID, status: 'OPEN' });
    api.patch.mockResolvedValue({ id: PLAN_ID, status: 'CLOSED' });

    const result = await montar();
    await result.current.fechar.mutateAsync();

    expect(api.post).toHaveBeenCalledWith(expect.stringContaining('/weekly-plans'), {
      weekStartDate: semana.inicioIso,
      weekEndDate: semana.fimIso,
    });
    expect(api.patch).toHaveBeenCalledWith(
      expect.stringContaining(`/weekly-plans/${PLAN_ID}/close`), {},
    );
  });

  it('fecha direto quando a semana já tem plano aberto', async () => {
    api.get.mockImplementation((url) => {
      if (url.includes('weekStart')) {
        return Promise.resolve({ id: PLAN_ID, status: 'OPEN', weekStartDate: semana.inicioIso });
      }
      return naoEncontrado();
    });
    api.patch.mockResolvedValue({ id: PLAN_ID, status: 'CLOSED' });

    const result = await montar();
    await result.current.fechar.mutateAsync();

    // Nada de POST: o plano já existia, e criar de novo duplicaria a semana
    expect(api.post).not.toHaveBeenCalled();
    expect(api.patch).toHaveBeenCalledWith(
      expect.stringContaining(`/weekly-plans/${PLAN_ID}/close`), {},
    );
  });
});
