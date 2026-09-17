// AC1 — "Dado que o usuário está logado e na aba de Configurações, quando
// acessar a seção 'Dados', então o sistema exibe a opção 'Exportar meus dados'."
// Também cobre o caminho completo daí até o arquivo baixado.
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import Configuracoes from '@/features/settings/pages/Configuracoes';
import { api, baixarArquivo } from '@/api/client';
import { salvarArquivo } from '@/features/settings/lib/exportacao';

// A página inteira (sidebar, conta, categorias) fala com o backend por este
// módulo; mocká-lo isola o teste sem precisar de servidor.
vi.mock('@/api/client', () => ({
  api: {
    get: vi.fn().mockResolvedValue([]),
    post: vi.fn().mockResolvedValue(null),
    put: vi.fn().mockResolvedValue(null),
    patch: vi.fn().mockResolvedValue(null),
    remove: vi.fn().mockResolvedValue(null),
  },
  getOuNull: vi.fn().mockResolvedValue(null),
  baixarArquivo: vi.fn(),
  ApiError: class ApiError extends Error {},
}));

vi.mock('@/features/settings/lib/exportacao', async (importOriginal) => ({
  ...(await importOriginal()),
  salvarArquivo: vi.fn(), // não dá para disparar download de verdade no jsdom
}));

function renderizar() {
  const qc = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={qc}>
      <MemoryRouter initialEntries={['/configuracoes']}>
        <Configuracoes />
      </MemoryRouter>
    </QueryClientProvider>,
  );
}

// A seção "Dados" — o cartão logo abaixo do título de mesmo nome.
function secaoDados() {
  return screen.getByText('Dados').closest('.set-section');
}

describe('Configurações › seção Dados', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    api.get.mockResolvedValue([]);
    api.post.mockResolvedValue(null);
  });

  it('exibe a opção "Exportar meus dados" dentro da seção Dados', () => {
    renderizar();

    const secao = secaoDados();
    expect(secao).toBeInTheDocument();
    expect(within(secao).getByText('Exportar meus dados', { selector: '.set-row__label' })).toBeInTheDocument();
    expect(within(secao).getByRole('button', { name: 'Exportar meus dados' })).toBeEnabled();
    // A opção deixou de ser "(em breve)" com os botões travados.
    expect(within(secao).queryByText(/em breve/i)).not.toBeInTheDocument();
  });

  it('abre a janela de confirmação com a escolha entre CSV e JSON', async () => {
    renderizar();

    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    await userEvent.click(within(secaoDados()).getByRole('button', { name: 'Exportar meus dados' }));

    const dialogo = screen.getByRole('dialog', { name: 'Exportar meus dados' });
    expect(within(dialogo).getAllByRole('radio').map((r) => r.value)).toEqual(['csv', 'json']);
  });

  it('baixa o arquivo no formato escolhido e confirma o nome ao usuário', async () => {
    api.post.mockResolvedValue({ jobId: 'job-1', status: 'PENDING' });
    api.get.mockImplementation((url) => Promise.resolve(
      url.includes('/me/exports/job-1')
        ? { status: 'COMPLETED', downloadUrl: 'http://temporary-download' }
        : [],
    ));
    baixarArquivo.mockResolvedValue({
      blob: new Blob(['{}']),
      nomeArquivo: 'export_tarefas_2026-07-27.json',
    });
    renderizar();

    await userEvent.click(within(secaoDados()).getByRole('button', { name: 'Exportar meus dados' }));
    await userEvent.click(screen.getByRole('radio', { name: /JSON/ }));
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));

    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(api.post).toHaveBeenCalledWith(expect.stringContaining('/me/exports?format=json'));
    expect(api.get).toHaveBeenCalledWith(expect.stringContaining('/me/exports/job-1'));
    expect(baixarArquivo).toHaveBeenCalledWith('http://temporary-download');
    expect(salvarArquivo).toHaveBeenCalledWith(expect.any(Blob), 'export_tarefas_2026-07-27.json');
    expect(within(secaoDados()).getByText('export_tarefas_2026-07-27.json')).toBeInTheDocument();
  });

  it('mantém o modal aberto e mostra a mensagem quando a exportação falha', async () => {
    api.post.mockResolvedValue({ jobId: 'job-1', status: 'PENDING' });
    api.get.mockImplementation((url) => Promise.resolve(
      url.includes('/me/exports/job-1') ? { status: 'FAILED', error: 'Erro 500' } : [],
    ));
    renderizar();

    await userEvent.click(within(secaoDados()).getByRole('button', { name: 'Exportar meus dados' }));
    await userEvent.click(screen.getByRole('button', { name: 'Exportar' }));

    expect(await screen.findByText('Erro 500')).toBeInTheDocument();
    expect(screen.getByRole('dialog')).toBeInTheDocument();
    expect(salvarArquivo).not.toHaveBeenCalled();
  });
});
