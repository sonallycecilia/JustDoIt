import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { salvarArquivo } from '@/features/settings/lib/exportacao';

describe('salvarArquivo', () => {
  beforeEach(() => {
    vi.useFakeTimers();
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true,
      value: vi.fn(() => 'blob:exportacao'),
    });
    Object.defineProperty(URL, 'revokeObjectURL', {
      configurable: true,
      value: vi.fn(),
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.restoreAllMocks();
  });

  it('mantém o Blob disponível até o navegador iniciar o download', () => {
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {});
    const blob = new Blob(['tarefas'], { type: 'text/csv' });

    salvarArquivo(blob, 'export_tarefas.csv');

    const link = document.querySelector('a[download="export_tarefas.csv"]');
    expect(URL.createObjectURL).toHaveBeenCalledWith(blob);
    expect(link).toHaveAttribute('href', 'blob:exportacao');
    expect(click).toHaveBeenCalledOnce();
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();

    vi.advanceTimersByTime(59_999);
    expect(link).toBeInTheDocument();
    expect(URL.revokeObjectURL).not.toHaveBeenCalled();

    vi.advanceTimersByTime(1);
    expect(link).not.toBeInTheDocument();
    expect(URL.revokeObjectURL).toHaveBeenCalledWith('blob:exportacao');
  });
});
