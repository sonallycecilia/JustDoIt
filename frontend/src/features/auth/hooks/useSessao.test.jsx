import { useEffect } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, renderHook, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { estaLogado, gravarSessao, lerSessao, limparSessao } from '@/api/session';
import { useEncerrarSessao, useIniciarSessao } from '@/features/auth/hooks/useSessao';

vi.mock('@/api/client', () => ({
  api: { post: vi.fn(), get: vi.fn(), put: vi.fn(), patch: vi.fn(), remove: vi.fn() },
  ApiError: class ApiError extends Error {},
}));

vi.mock('@marsidev/react-turnstile', () => ({
  Turnstile: ({ onSuccess }) => {
    useEffect(() => {
      onSuccess?.('fake-turnstile-token');
    }, [onSuccess]);
    return null;
  },
}));

const { api } = await import('@/api/client');
const { default: LoginForm } = await import('@/features/auth/components/LoginForm');

function envolver(qc) {
  return function Wrapper({ children }) {
    return (
      <QueryClientProvider client={qc}>
        <MemoryRouter>{children}</MemoryRouter>
      </QueryClientProvider>
    );
  };
}

afterEach(() => {
  limparSessao();
  vi.clearAllMocks();
});

describe('troca de sessão na mesma aba', () => {
  it('descarta o cache da conta anterior ao entrar', () => {
    const qc = new QueryClient();
    qc.setQueryData(['tarefas'], [{ id: 'tarefa-do-usuario-a' }]);

    const { result } = renderHook(() => useIniciarSessao(), { wrapper: envolver(qc) });
    act(() => result.current({ accessToken: 'token-b', refreshToken: 'refresh-b' }, { lembrar: true }));

    expect(qc.getQueryData(['tarefas'])).toBeUndefined();
    // O token novo entra ANTES do clear: o refetch disparado pela limpeza
    // precisa sair já autenticado como o usuário que acabou de entrar.
    expect(lerSessao().accessToken).toBe('token-b');
  });

  it('descarta o cache e a sessão ao sair', () => {
    gravarSessao({ accessToken: 'token-a', refreshToken: 'refresh-a' }, { lembrar: true });
    const qc = new QueryClient();
    qc.setQueryData(['categorias'], [{ id: 'categoria-do-usuario-a' }]);
    qc.setQueryData(['usuario'], { name: 'Usuário A' });

    const { result } = renderHook(() => useEncerrarSessao(), { wrapper: envolver(qc) });
    act(() => result.current());

    expect(qc.getQueryData(['categorias'])).toBeUndefined();
    expect(qc.getQueryData(['usuario'])).toBeUndefined();
    expect(estaLogado()).toBe(false);
  });

  it('login não deixa tarefas nem categorias do usuário anterior em cache', async () => {
    api.post.mockResolvedValue({ accessToken: 'token-b', refreshToken: 'refresh-b' });
    const qc = new QueryClient();
    qc.setQueryData(['tarefas'], [{ id: 'tarefa-do-usuario-a' }]);
    qc.setQueryData(['categorias'], [{ id: 'categoria-do-usuario-a' }]);

    render(<LoginForm />, { wrapper: envolver(qc) });
    await userEvent.type(screen.getByLabelText('E-mail'), 'b@exemplo.com');
    await userEvent.type(screen.getByLabelText('Senha'), 'senha-de-teste');
    await userEvent.click(screen.getByRole('button', { name: 'Entrar' }));

    await waitFor(() => expect(lerSessao()?.accessToken).toBe('token-b'));
    expect(qc.getQueryData(['tarefas'])).toBeUndefined();
    expect(qc.getQueryData(['categorias'])).toBeUndefined();
  });
});
