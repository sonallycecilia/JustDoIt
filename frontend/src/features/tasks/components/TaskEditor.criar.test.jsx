import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { endpoints } from '@/api/endpoints';

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

// O título é contentEditable e é lido do DOM no momento do envio.
function digitarTitulo(container, texto) {
  const titulo = container.querySelector('.edit-title');
  titulo.textContent = texto;
  return titulo;
}

const postsEmTasks = () => api.post.mock.calls.filter(([url]) => url === endpoints.tasks.create);

beforeEach(() => {
  api.get.mockResolvedValue([]);
  api.post.mockImplementation((url) => (url === endpoints.tasks.create
    ? Promise.resolve({ id: 'tarefa-nova', title: 'Comprar pão' })
    : Promise.resolve({})));
});

afterEach(() => {
  vi.clearAllMocks();
  // Esvazia o texto em vez de remover o nó: lib/toast guarda a referência do
  // elemento entre chamadas, e removê-lo faria o próximo toast escrever num nó
  // solto do documento.
  document.querySelectorAll('.jdi-toast').forEach((el) => { el.textContent = ''; });
});

describe('registrar tarefa em /tasks/nova', () => {
  it('envia a antecedência escolhida junto do horário da tarefa', async () => {
    const { container } = renderizarNova();
    digitarTitulo(container, 'Reunião importante');

    await userEvent.click(screen.getByRole('button', { name: 'Hora' }));
    await userEvent.click(screen.getByRole('button', { name: '10' }));
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'Lembrete da tarefa' }), '60');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    await screen.findByText('Cheguei na To Do');
    expect(postsEmTasks()).toHaveLength(1);
    expect(postsEmTasks()[0][1]).toEqual(expect.objectContaining({
      dueTime: '10:00',
      reminderMinutesBefore: 60,
    }));
  });

  it('conclui a criação mesmo se uma gravação secundária falhar', async () => {
    // O módulo ligado abaixo dispara o PUT /module-config, que aqui falha.
    api.put.mockRejectedValue(new Error('500'));

    const { container } = renderizarNova();
    digitarTitulo(container, 'Comprar pão');
    await userEvent.click(screen.getByRole('button', { name: 'Tempo' }));
    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    // A tarefa existe no banco: a tela precisa seguir em frente, não fingir falha.
    expect(await screen.findByText('Cheguei na To Do')).toBeInTheDocument();
    // ...e avisar que o resto não foi junto, em vez de silenciar.
    await waitFor(() => {
      expect(document.body.textContent).toContain('Tarefa criada, mas alguns detalhes não foram salvos.');
    });
    // O ponto central: uma única tarefa criada, nunca duas.
    expect(postsEmTasks()).toHaveLength(1);
  });

  it('avisa que falta o título em vez de deixar o clique sem resposta', async () => {
    const { container } = renderizarNova();
    // Só a descrição preenchida: era aqui que o clique morria em silêncio.
    container.querySelector('.edit-desc').textContent = 'Sem título ainda';

    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    await waitFor(() => {
      expect(document.body.textContent).toContain('Dê um nome à tarefa antes de registrar.');
    });
    expect(postsEmTasks()).toHaveLength(0);
    expect(screen.queryByText('Cheguei na To Do')).not.toBeInTheDocument();
  });

  it('avisa quando o POST responde sem id, em vez de ficar parado na página', async () => {
    api.post.mockImplementation((url) => (url === endpoints.tasks.create
      ? Promise.resolve(null) // 201 com corpo vazio
      : Promise.resolve({})));

    const { container } = renderizarNova();
    digitarTitulo(container, 'Comprar pão');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    await waitFor(() => {
      expect(document.body.textContent).toContain('não devolveu o identificador');
    });
    expect(screen.queryByText('Cheguei na To Do')).not.toBeInTheDocument();
  });

  it('não navega nem cria nada quando o POST da tarefa falha', async () => {
    api.post.mockImplementation((url) => (url === endpoints.tasks.create
      ? Promise.reject(new Error('Category not found'))
      : Promise.resolve({})));

    const { container } = renderizarNova();
    digitarTitulo(container, 'Comprar pão');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    await waitFor(() => expect(document.body.textContent).toContain('Category not found'));
    expect(screen.queryByText('Cheguei na To Do')).not.toBeInTheDocument();
  });

  it('permite criar com Genérico quando a API de categorias falha', async () => {
    api.get.mockRejectedValue(new Error('categorias indisponíveis'));

    const { container } = renderizarNova();

    const seletor = await screen.findByRole('button', { name: 'Genérico' });
    expect(seletor).toBeEnabled();
    expect(screen.queryByRole('button', { name: 'Todas as categorias' })).not.toBeInTheDocument();

    await userEvent.click(seletor);
    expect(screen.getByRole('option', { name: 'Genérico' })).toBeInTheDocument();

    await userEvent.click(screen.getByRole('option', { name: 'Genérico' }));
    digitarTitulo(container, 'Tarefa sem categoria remota');
    await userEvent.click(screen.getByRole('button', { name: 'Registrar tarefa' }));

    await screen.findByText('Cheguei na To Do');
    expect(postsEmTasks()[0][1]).toEqual(expect.objectContaining({ categoryId: null }));
  });
});
