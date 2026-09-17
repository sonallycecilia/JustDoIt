import { MemoryRouter } from 'react-router-dom';
import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Sidebar from '@/components/Sidebar';

vi.mock('@/features/categories/hooks/useCategories', () => ({
  useCategorias: () => ({ data: [] }),
  useRemoverCategoria: () => ({ mutate: vi.fn() }),
}));
vi.mock('@/features/tasks/hooks/useTasks', () => ({
  useTarefas: () => ({ data: [] }),
  useAtualizarTarefa: () => ({ mutate: vi.fn() }),
  useRemoverTarefa: () => ({ mutate: vi.fn() }),
}));
vi.mock('@/features/auth/hooks/useConta', () => ({
  useConta: () => ({ data: { name: 'Usuário Teste' } }),
}));
vi.mock('@/features/auth/hooks/useSessao', () => ({
  useEncerrarSessao: () => vi.fn(),
}));
vi.mock('@/api/session', () => ({ lerSessao: () => ({ name: 'Usuário Teste' }) }));
vi.mock('@/features/categories/components/CategoryModal', () => ({ default: () => null }));
vi.mock('@/features/feedback/components/DevelopmentChatModal', () => ({
  default: ({ aberto }) => (aberto ? <div role="dialog" aria-label="Chat com o desenvolvimento" /> : null),
}));
vi.mock('@/features/notifications/components/NotificationCenter', () => ({
  default: () => <button className="floating-action floating-action--notifications" type="button" aria-label="Notificações" />,
}));

describe('Sidebar — contato com desenvolvimento', () => {
  beforeEach(() => {
    localStorage.clear();
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 1024 });
  });

  it('exibe o botão circular de chat acima das notificações e abre a conversa', async () => {
    render(<MemoryRouter><Sidebar /></MemoryRouter>);

    const chat = screen.getByRole('button', { name: 'Chat com o desenvolvimento' });
    const notifications = screen.getByRole('button', { name: 'Notificações' });
    expect(chat).toHaveClass('floating-action', 'floating-action--chat');
    expect(notifications).toHaveClass('floating-action', 'floating-action--notifications');
    expect(chat).toHaveAttribute('title', 'Chat com o desenvolvimento');
    expect(chat).toHaveAttribute('aria-expanded', 'false');

    await userEvent.click(chat);
    expect(screen.getByRole('dialog', { name: 'Chat com o desenvolvimento' })).toBeInTheDocument();
    expect(chat).toHaveAttribute('aria-expanded', 'true');
  });

  it('redimensiona pela borda e preserva a largura escolhida', () => {
    const { container, unmount } = render(<MemoryRouter><Sidebar /></MemoryRouter>);
    const divisor = screen.getByRole('separator', { name: 'Redimensionar menu lateral' });

    fireEvent.pointerDown(divisor, { pointerId: 1, clientX: 264 });
    fireEvent.pointerMove(document, { pointerId: 1, clientX: 360 });
    fireEvent.pointerUp(document, { pointerId: 1, clientX: 360 });

    expect(container.querySelector('.sidebar').style.getPropertyValue('--sidebar-current-width')).toBe('360px');
    expect(localStorage.getItem('jdi-sidebar-width')).toBe('360px');

    unmount();
    const novaMontagem = render(<MemoryRouter><Sidebar /></MemoryRouter>);
    expect(novaMontagem.container.querySelector('.sidebar').style.getPropertyValue('--sidebar-current-width')).toBe('360px');
  });

  it('permite redimensionar a borda pelo teclado', () => {
    const { container } = render(<MemoryRouter><Sidebar /></MemoryRouter>);
    const divisor = screen.getByRole('separator', { name: 'Redimensionar menu lateral' });

    fireEvent.keyDown(divisor, { key: 'ArrowRight' });

    expect(container.querySelector('.sidebar').style.getPropertyValue('--sidebar-current-width')).toBe('280px');
    expect(divisor).toHaveAttribute('aria-valuenow', '280');
  });

  it('abre e redimensiona o menu mobile arrastando toda a borda direita', () => {
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    const { container } = render(<MemoryRouter><Sidebar /></MemoryRouter>);
    const sidebar = container.querySelector('.sidebar');
    vi.spyOn(sidebar, 'getBoundingClientRect').mockReturnValue({ width: 64 });
    const divisor = screen.getByRole('separator', { name: 'Redimensionar menu lateral' });

    fireEvent.pointerDown(divisor, { pointerId: 3, clientX: 64 });
    fireEvent.pointerMove(document, { pointerId: 3, clientX: 240 });
    fireEvent.pointerUp(document, { pointerId: 3, clientX: 240 });

    expect(sidebar).toHaveClass('sidebar--mobile-open');
    expect(sidebar.style.getPropertyValue('--sidebar-mobile-current-width')).toBe('240px');
    expect(localStorage.getItem('jdi-sidebar-mobile-width')).toBe('240px');
    expect(divisor).toHaveAttribute('aria-valuenow', '240');
  });
});
