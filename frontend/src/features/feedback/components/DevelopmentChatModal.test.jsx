import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api } from '@/api/client';
import DevelopmentChatModal from '@/features/feedback/components/DevelopmentChatModal';

vi.mock('@/api/client', () => ({
  api: { get: vi.fn(), post: vi.fn() },
}));

function renderModal(props = {}) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  const onFechar = vi.fn();
  render(
    <QueryClientProvider client={queryClient}>
      <DevelopmentChatModal aberto onFechar={onFechar} {...props} />
    </QueryClientProvider>,
  );
  return { onFechar };
}

describe('DevelopmentChatModal', () => {
  beforeEach(() => {
    api.get.mockReset();
    api.post.mockReset();
    api.get.mockResolvedValue([]);
  });

  it('carrega e apresenta o histórico da conversa', async () => {
    api.get.mockResolvedValue([{
      id: 'message-1',
      sender: 'DEVELOPMENT',
      content: 'Recebemos sua mensagem.',
      createdAt: '2026-08-15T10:00:00',
    }]);

    renderModal();

    expect(await screen.findByText('Recebemos sua mensagem.')).toBeInTheDocument();
    expect(screen.getByText('Desenvolvimento')).toBeInTheDocument();
    expect(api.get).toHaveBeenCalledWith(expect.stringMatching(/\/support\/messages$/));
  });

  it('valida conteúdo vazio sem chamar o backend', async () => {
    renderModal();
    const input = screen.getByRole('textbox', { name: 'Mensagem' });

    await waitFor(() => expect(input).toHaveFocus());
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(screen.getByText('Escreva uma mensagem antes de enviar.')).toBeInTheDocument();
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(api.post).not.toHaveBeenCalled();
  });

  it('envia a mensagem com contexto e adiciona a resposta ao histórico', async () => {
    api.post.mockResolvedValue({
      id: 'message-2',
      sender: 'USER',
      content: 'O botão travou.',
      createdAt: '2026-08-15T11:00:00',
    });
    renderModal();

    await userEvent.type(screen.getByRole('textbox', { name: 'Mensagem' }), '  O botão travou.  ');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    expect(api.post).toHaveBeenCalledWith(expect.stringMatching(/\/support\/messages$/), {
      content: 'O botão travou.',
      pageUrl: window.location.href,
      userAgent: window.navigator.userAgent,
    });
    expect(await screen.findByText('O botão travou.')).toBeInTheDocument();
    expect(screen.getByText('Mensagem enviada.')).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Mensagem' })).toHaveValue('');
  });

  it('preserva o texto quando o envio falha e permite tentar novamente', async () => {
    api.post.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({
      id: 'message-3',
      sender: 'USER',
      content: 'Não consigo salvar',
      createdAt: '2026-08-15T12:00:00',
    });
    renderModal();
    const input = screen.getByRole('textbox', { name: 'Mensagem' });

    await userEvent.type(input, 'Não consigo salvar');
    await userEvent.click(screen.getByRole('button', { name: 'Enviar' }));

    expect(await screen.findByText('Não foi possível enviar a mensagem. Verifique sua conexão e tente novamente.')).toBeInTheDocument();
    expect(input).toHaveValue('Não consigo salvar');
    await userEvent.click(screen.getByRole('button', { name: 'Tentar novamente' }));

    expect(await screen.findByText('Mensagem enviada.')).toBeInTheDocument();
    expect(api.post).toHaveBeenCalledTimes(2);
  });
});
