import { fireEvent, render, screen } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import NotificationCenter from './NotificationCenter';

const mocks = vi.hoisted(() => ({
  deleteNotification: vi.fn(),
  markRead: vi.fn(),
  navigate: vi.fn(),
}));

vi.mock('react-router-dom', () => ({
  useNavigate: () => mocks.navigate,
}));

vi.mock('@/features/notifications/hooks/useNotifications', () => ({
  useNotifications: () => ({
    data: [{
      id: 'notification-1',
      taskId: 'task-1',
      type: 'TASK_REMINDER',
      title: 'Revisar relatório',
      message: 'A tarefa começa em 15 minutos.',
      read: false,
      createdAt: '2026-08-11T09:00:00',
    }],
    isLoading: false,
  }),
  useMarkNotificationRead: () => ({ mutate: mocks.markRead }),
  useDeleteNotification: () => ({
    mutate: mocks.deleteNotification,
    isPending: false,
    variables: undefined,
  }),
}));

describe('NotificationCenter', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    sessionStorage.clear();
  });

  it('exclui um alerta sem abrir a tarefa', () => {
    render(<NotificationCenter />);

    fireEvent.click(screen.getByRole('button', { name: /Alertas/ }));
    fireEvent.click(screen.getByRole('button', { name: 'Excluir alerta: Revisar relatório' }));

    expect(mocks.deleteNotification).toHaveBeenCalledWith(
      'notification-1',
      expect.objectContaining({ onError: expect.any(Function) }),
    );
    expect(mocks.markRead).not.toHaveBeenCalled();
    expect(mocks.navigate).not.toHaveBeenCalled();
  });
});
