import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import RecurringDeleteModal from '@/features/tasks/components/RecurringDeleteModal';
import { selecionarOcorrenciasPertinentes } from '@/features/tasks/pages/Todo';

describe('To Do de tarefas recorrentes', () => {
  it('exibe somente a tarefa original de cada série', () => {
    const tarefas = [
      { id: 'avulsa', dataIso: '2026-08-10', done: false },
      { id: 'modelo', cycleType: 'WEEKLY', dataIso: '2026-08-01', done: true },
      { id: 'ocorrencia-2', seriesId: 'modelo', dataIso: '2026-08-08', done: false },
      { id: 'ocorrencia-3', seriesId: 'modelo', dataIso: '2026-08-15', done: false },
    ];

    expect(selecionarOcorrenciasPertinentes(tarefas).map((t) => t.id))
      .toEqual(['avulsa', 'modelo']);
  });

  it('oferece exclusão somente da instância ou de toda a série', async () => {
    const escolher = vi.fn();
    render(
      <RecurringDeleteModal
        tarefa={{ id: 'ocorrencia-2', seriesId: 'modelo' }}
        processando={false}
        onEscolher={escolher}
        onFechar={vi.fn()}
      />,
    );

    const dialogo = screen.getByRole('dialog', { name: 'Excluir tarefa recorrente' });
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Somente esta ocorrência' }));
    await userEvent.click(within(dialogo).getByRole('button', { name: 'Todas as ocorrências' }));

    expect(escolher).toHaveBeenNthCalledWith(1, 'INSTANCE');
    expect(escolher).toHaveBeenNthCalledWith(2, 'SERIES');
  });
});