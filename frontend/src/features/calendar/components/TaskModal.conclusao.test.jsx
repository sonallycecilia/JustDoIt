import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it, vi } from 'vitest';
import { TaskModal } from '@/features/calendar/components/WeeklyCalendar';

const evento = {
  id: 'task-tarefa-1',
  taskId: 'tarefa-1',
  titulo: 'Publicar correção',
  cat: 'generico',
  catNome: 'Genérico',
  prio: 'normal',
  ini: 9,
  fim: 10,
  done: false,
};

function renderModal(extra = {}) {
  const props = {
    ev: evento,
    dia: { iso: '2026-08-16', num: 16, mes: 7, dow: 'DOM' },
    categorias: [],
    onClose: vi.fn(),
    onUpdate: vi.fn(),
    onDelete: vi.fn(),
    onToggle: vi.fn(),
    ...extra,
  };
  render(<MemoryRouter><TaskModal {...props} /></MemoryRouter>);
  return props;
}

describe('conclusão de tarefa no modal do calendário', () => {
  it('permite concluir uma tarefa aberta pela visão mensal', async () => {
    const props = renderModal();

    await userEvent.click(screen.getByRole('button', { name: 'Marcar como concluída' }));

    expect(props.onToggle).toHaveBeenCalledWith(evento);
  });

  it('permite reabrir uma tarefa concluída', () => {
    renderModal({ ev: { ...evento, done: true } });

    expect(screen.getByRole('button', { name: 'Reabrir tarefa' })).toBeInTheDocument();
  });

  it('bloqueia cliques repetidos enquanto salva', () => {
    renderModal({ processando: true });

    expect(screen.getByRole('button', { name: 'Salvando…' })).toBeDisabled();
  });
});
