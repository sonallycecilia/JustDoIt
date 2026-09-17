import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { endpoints } from '@/api/endpoints';
import { lerRascunho, limparRascunho } from '@/features/tasks/hooks/useRascunhoTarefa';

vi.mock('@/api/client', () => ({
  api: { get: vi.fn(), post: vi.fn(), put: vi.fn(), patch: vi.fn(), remove: vi.fn() },
  getOuNull: vi.fn(async () => null),
  ApiError: class ApiError extends Error {},
}));

const { api } = await import('@/api/client');
const { default: TaskDetail } = await import('@/features/tasks/pages/TaskDetail');

function renderizarNova() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/tasks/nova']}>
        <Routes>
          <Route path="/tasks/nova" element={<TaskDetail />} />
          <Route path="/todo" element={<div>Cheguei na To Do</div>} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

const titulo = (container) => container.querySelector('.edit-title');

// O título é contentEditable: digitar de verdade é o que dispara o onInput que
// alimenta o rascunho. Atribuir textContent não emitiria evento nenhum.
const digitarTitulo = (container, texto) => userEvent.type(titulo(container), texto);

beforeEach(() => {
  limparRascunho();
  api.get.mockResolvedValue([]);
  api.put.mockResolvedValue({});
  api.post.mockImplementation((url) => (url === endpoints.tasks.create
    ? Promise.resolve({ id: 'tarefa-nova' })
    : Promise.resolve({})));
});

afterEach(() => {
  vi.clearAllMocks();
  limparRascunho();
});

describe('rascunho da tarefa em criação', () => {
  it('guarda o que foi digitado ao sair da página sem registrar', async () => {
    const { container, unmount } = renderizarNova();
    await digitarTitulo(container, 'Levar o cachorro ao veterinário');
    await userEvent.click(screen.getByRole('button', { name: 'Prioridade' }));

    unmount(); // sair pela sidebar / fechar a aba

    await waitFor(() => expect(lerRascunho()?.titulo).toBe('Levar o cachorro ao veterinário'));
    expect(lerRascunho().mods.prioridade).toBe(true);
  });

  it('repõe o rascunho ao voltar para /tasks/nova', async () => {
    const primeira = renderizarNova();
    await digitarTitulo(primeira.container, 'Comprar pão');
    primeira.unmount();
    await waitFor(() => expect(lerRascunho()).not.toBeNull());

    const segunda = renderizarNova();
    await waitFor(() => expect(titulo(segunda.container).textContent).toBe('Comprar pão'));
  });

  it('descarta o rascunho depois de registrar a tarefa', async () => {
    const { container } = renderizarNova();
    await digitarTitulo(container, 'Comprar pão');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    expect(await screen.findByText('Cheguei na To Do')).toBeInTheDocument();
    // Inclusive depois do desmonte: a gravação de despedida não pode
    // ressuscitar o rascunho de uma tarefa que já está no banco.
    await waitFor(() => expect(lerRascunho()).toBeNull());
  });

  it('não guarda rascunho de uma página apenas aberta e abandonada', async () => {
    const { unmount } = renderizarNova();
    unmount();

    expect(lerRascunho()).toBeNull();
  });
});
