// Todo.perf.test.jsx
import { render, screen, fireEvent } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { vi } from 'vitest';
import Todo from '@/features/tasks/pages/Todo';

// Só o useCategorias (faz fetch real) é substituído; useRemoverCategoria,
// useCriarCategoria, useAtualizarCategoria, categoriaPorId e CAT_GENERICO
// continuam sendo os reais — são usados pelo Sidebar e pelo CategoryModal
// que o Sidebar sempre monta (mesmo fechado), e são mutações/funções puras
// que só rodam se alguém de fato chamar `.mutate()` ou invocá-las.
vi.mock('@/features/categories/hooks/useCategories', async (importOriginal) => {
  const actual = await importOriginal();
  return {
    ...actual,
    useCategorias: () => ({ data: [{ id: 'c1', nome: 'Geral', cor: '#000' }] }),
  };
});

vi.mock('@/features/weekly-closure/hooks/useWeeklyClosure', () => ({
  useWeeklyClosure: () => ({
    previewData: null, isModalOpen: false, setIsModalOpen: vi.fn(),
    loadPreview: vi.fn(), handleSubmittingClosure: vi.fn(), isLoading: false,
  }),
}));

function gerarTarefas(n) {
  return Array.from({ length: n }, (_, i) => ({
    id: `t${i}`, title: `Tarefa ${i}`, status: i % 3 === 0 ? 'COMPLETED' : 'PENDING',
    dueDate: '2026-08-10', priority: 'NORMAL', categoryId: null,
  }));
}

function renderComVolume(n) {
  // staleTime: Infinity evita que o React Query dispare um refetch em
  // background contra a API real assim que o componente monta — o jsdom
  // não tem servidor, e sem isso o teste fica ruidoso/instável.
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false, staleTime: Infinity } } });
  qc.setQueryData(['tarefas'], gerarTarefas(n));
  render(
    <QueryClientProvider client={qc}>
      <MemoryRouter><Todo /></MemoryRouter>
    </QueryClientProvider>,
  );
}

it.each([100, 500, 2000, 5000])('filtro com %i tarefas fica sob 500ms (P95)', async (n) => {
  renderComVolume(n);
  const chip = await screen.findByRole('button', { name: 'Concluídas' });
  const amostras = [];

  for (let i = 0; i < 20; i++) {
    const inicio = performance.now();
    fireEvent.click(chip);
    amostras.push(performance.now() - inicio);
  }
  amostras.sort((a, b) => a - b);
  const p95 = amostras[Math.floor(amostras.length * 0.95)];
  expect(p95).toBeLessThan(500);
});