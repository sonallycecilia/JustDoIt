import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ConfirmDialog } from '@/features/calendar/components/WeeklyCalendar';
import { endpoints } from '@/api/endpoints';

// O parâmetro de onConfirm é o escopo da exclusão (INSTANCE|SERIES) e vai
// direto para a query string do DELETE. Se o evento de clique vazar para lá,
// o backend recusa e a tarefa some da tela só até o próximo refetch.
describe('confirmação de exclusão no calendário', () => {
  it('confirma sem repassar o evento de clique como escopo', async () => {
    const confirmar = vi.fn();
    render(
      <ConfirmDialog titulo="Excluir tarefa" confirmar="Excluir"
        onConfirm={confirmar} onCancel={vi.fn()}>
        Tem certeza?
      </ConfirmDialog>,
    );

    await userEvent.click(screen.getByRole('button', { name: /Excluir/ }));

    expect(confirmar).toHaveBeenCalledTimes(1);
    expect(confirmar).toHaveBeenCalledWith();
  });

  it('monta o DELETE com um escopo que o backend aceita', () => {
    expect(endpoints.tasks.remove('abc')).toMatch(/\/tasks\/abc\?scope=INSTANCE$/);
    expect(endpoints.tasks.remove('abc', 'SERIES')).toMatch(/\/tasks\/abc\?scope=SERIES$/);
  });
});
