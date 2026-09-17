import { act, renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, baixarArquivo } from '@/api/client';
import { useExportarDados } from '@/features/settings/hooks/useExportarDados';
import { nomePadrao, salvarArquivo } from '@/features/settings/lib/exportacao';

vi.mock('@/api/client', () => ({
  api: { post: vi.fn(), get: vi.fn() },
  baixarArquivo: vi.fn(),
}));
vi.mock('@/features/settings/lib/exportacao', async (original) => ({
  ...(await original()),
  salvarArquivo: vi.fn(),
}));

function criarWrapper() {
  const qc = new QueryClient({ defaultOptions: { mutations: { retry: false } } });
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

async function exportar(formato) {
  const { result } = renderHook(() => useExportarDados(), { wrapper: criarWrapper() });
  await act(async () => {
    await result.current.mutateAsync(formato).catch(() => {});
  });
  return result;
}

describe('useExportarDados assíncrono', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.post.mockResolvedValue({ jobId: 'job-1', status: 'PENDING' });
  });

  it.each([
    ['csv', 'export_tarefas_2026-07-27.csv'],
    ['json', 'export_tarefas_2026-07-27.json'],
  ])('cria job %s, consulta o status e baixa pelo link temporário', async (formato, nome) => {
    const blob = new Blob(['x']);
    api.get.mockResolvedValue({
      status: 'COMPLETED',
      downloadUrl: `http://localhost:8081/me/exports/job-1/download?expires=1&token=abc`,
    });
    baixarArquivo.mockResolvedValue({ blob, nomeArquivo: nome });

    const result = await exportar(formato);

    expect(api.post).toHaveBeenCalledWith(expect.stringContaining(`/me/exports?format=${formato}`));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining('/me/exports/job-1'));
    expect(baixarArquivo).toHaveBeenCalledWith(expect.stringContaining('token=abc'));
    expect(salvarArquivo).toHaveBeenCalledWith(blob, nome);
    await waitFor(() => expect(result.current.data).toBe(nome));
  });

  it('usa o nome padrão quando Content-Disposition não chega ao JS', async () => {
    api.get.mockResolvedValue({ status: 'COMPLETED', downloadUrl: 'http://download' });
    baixarArquivo.mockResolvedValue({ blob: new Blob(['x']), nomeArquivo: null });

    await exportar('csv');

    expect(salvarArquivo).toHaveBeenCalledWith(expect.any(Blob), nomePadrao('csv'));
  });

  it('propaga a falha do worker sem tentar baixar arquivo', async () => {
    api.get.mockResolvedValue({ status: 'FAILED', error: 'Limite de tamanho excedido' });

    const result = await exportar('json');

    await waitFor(() => expect(result.current.error?.message).toBe('Limite de tamanho excedido'));
    expect(baixarArquivo).not.toHaveBeenCalled();
    expect(salvarArquivo).not.toHaveBeenCalled();
  });
});

describe('nomePadrao', () => {
  it('carimba a data local e a extensão', () => {
    const noite = new Date(2026, 6, 27, 23, 30);
    expect(nomePadrao('csv', noite)).toBe('export_tarefas_2026-07-27.csv');
    expect(nomePadrao('json', noite)).toBe('export_tarefas_2026-07-27.json');
  });
});
