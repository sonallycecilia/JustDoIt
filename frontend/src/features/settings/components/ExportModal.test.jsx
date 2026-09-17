// AC2 — "Dado que o usuário clicou em 'Exportar meus dados', quando abrir a
// janela de confirmação, então o sistema oferece a escolha entre CSV e JSON."
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import ExportModal from '@/features/settings/components/ExportModal';

function abrir(props = {}) {
  const onExportar = vi.fn();
  const onFechar = vi.fn();
  render(<ExportModal aberto onExportar={onExportar} onFechar={onFechar} {...props} />);
  return { onExportar, onFechar };
}

describe('ExportModal', () => {
  it('não renderiza nada enquanto fechado', () => {
    render(<ExportModal aberto={false} onExportar={vi.fn()} onFechar={vi.fn()} />);
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
  });

  it('oferece exatamente os formatos CSV e JSON', () => {
    abrir();
    const opcoes = screen.getAllByRole('radio');
    expect(opcoes.map((o) => o.value)).toEqual(['csv', 'json']);
    expect(screen.getByRole('radio', { name: /CSV/ })).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /JSON/ })).toBeInTheDocument();
  });

  it('exporta em CSV (formato pré-selecionado) ao confirmar', async () => {
    const { onExportar } = abrir();
    expect(screen.getByRole('radio', { name: /CSV/ })).toBeChecked();

    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));

    expect(onExportar).toHaveBeenCalledTimes(1);
    expect(onExportar).toHaveBeenCalledWith('csv');
  });

  it('exporta em JSON quando o usuário troca o formato', async () => {
    const { onExportar } = abrir();

    await userEvent.click(screen.getByRole('radio', { name: /JSON/ }));
    expect(screen.getByRole('radio', { name: /JSON/ })).toBeChecked();
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));

    expect(onExportar).toHaveBeenCalledTimes(1);
    expect(onExportar).toHaveBeenCalledWith('json');
  });

  it('anuncia o que vai no arquivo', () => {
    abrir();
    const texto = screen.getByRole('dialog').textContent;
    expect(texto).toMatch(/status de conclusão/i);
    expect(texto).toMatch(/categoria/i);
    expect(texto).toMatch(/estimativa/i);
    expect(texto).toMatch(/cronômetro/i);
    expect(texto).toMatch(/notas/i);
  });

  it('cancela sem exportar', async () => {
    const { onExportar, onFechar } = abrir();
    await userEvent.click(screen.getByRole('button', { name: 'Cancelar' }));
    expect(onFechar).toHaveBeenCalled();
    expect(onExportar).not.toHaveBeenCalled();
  });

  it('trava os controles e mostra o erro devolvido pelo backend', () => {
    abrir({ processando: true, erro: 'Erro 500' });
    expect(screen.getByRole('button', { name: 'Preparando arquivo…' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Cancelar' })).toBeDisabled();
    screen.getAllByRole('radio').forEach((r) => expect(r).toBeDisabled());
    expect(screen.getByText('Erro 500')).toBeInTheDocument();
  });
});
