// O dashboard lê a semana em uma chamada canônica do schedule-service, que
// reúne relatório e blocos e devolve snapshot quando fechada. O teste protege:
// não voltar ao fan-out por
// tarefa, as três séries não se misturarem, e o executado cair no dia em que foi
// trabalhado (e não no vencimento da tarefa).
import { renderHook, waitFor } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { api, ApiError } from '@/api/client';
import { useAnaliseSemanal } from '@/features/dashboard/hooks/useAnalytics';
import { dataIso, intervaloSemana } from '@/lib/utils';

vi.mock('@/api/client', async (original) => ({
  ...(await original()),
  api: { get: vi.fn() },
}));

function criarWrapper() {
  const qc = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return ({ children }) => <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}

// A semana corrente muda a cada execução, então tudo é montado a partir dela:
// datas fixas quebrariam o teste na semana seguinte.
const semana = intervaloSemana();

function diaDaSemana(indice) {
  const d = new Date(semana.inicio);
  d.setDate(semana.inicio.getDate() + indice);
  return dataIso(d);
}

function relatorio(dias) {
  const byDay = [0, 1, 2, 3, 4, 5, 6].map((i) => {
    const dia = {
      date: diaDaSemana(i),
      focusSeconds: 0,
      timerSeconds: 0,
      completedTasks: 0,
      focusSessions: 0,
      estimatedMinutes: 0,
      ...(dias[i] || {}),
    };
    // Como no backend: o executado do dia é a soma das duas origens.
    return { ...dia, actualSeconds: dia.focusSeconds + dia.timerSeconds };
  });
  return {
    from: semana.inicioIso,
    to: semana.fimIso,
    totalTasks: 0,
    completedTasks: 0,
    totalActualSeconds: byDay.reduce((s, d) => s + d.actualSeconds, 0),
    totalEstimatedMinutes: byDay.reduce((s, d) => s + d.estimatedMinutes, 0),
    byDay,
  };
}

// Bloco cru do schedule-service (o hook useBlocos traduz para ini/fim decimais).
function bloco(indiceDia, horaIni, horaFim) {
  const iso = diaDaSemana(indiceDia);
  return {
    id: `b${indiceDia}-${horaIni}`,
    taskId: null,
    date: iso,
    startDateTime: `${iso}T${String(horaIni).padStart(2, '0')}:00:00`,
    endDateTime: `${iso}T${String(horaFim).padStart(2, '0')}:00:00`,
    estimatedMinutes: (horaFim - horaIni) * 60,
  };
}

function responder({ report, blocks = [], status = 'OPEN', source = 'LIVE', dataStatus = 'COMPLETE' }) {
  api.get.mockImplementation((url) => {
    if (url.includes('/analytics/weeks/')) {
      return Promise.resolve({ report, timeBlocks: blocks, status, source, dataStatus });
    }
    return Promise.reject(new Error(`URL inesperada: ${url}`));
  });
}

async function analisar(tarefas, resposta) {
  responder(resposta);
  const { result } = renderHook(() => useAnaliseSemanal(tarefas), { wrapper: criarWrapper() });
  await waitFor(() => expect(result.current.carregando).toBe(false));
  return result;
}

describe('useAnaliseSemanal', () => {
  beforeEach(() => vi.clearAllMocks());

  it('busca relatório e agenda da semana em uma requisição canônica', async () => {
    await analisar([], { report: relatorio({}) });

    const urls = api.get.mock.calls.map(([url]) => url);
    expect(urls.filter((u) => u.includes('/analytics/weeks/'))).toHaveLength(1);
    expect(urls[0]).toContain(semana.inicioIso);
  });

  it('não chama mais /timer nem /focus-sessions por tarefa', async () => {
    const tarefas = [
      { id: 'a', dataIso: diaDaSemana(0), duracaoMin: 60, done: false, cat: 'Estudo' },
      { id: 'b', dataIso: diaDaSemana(1), duracaoMin: 30, done: true, cat: 'Estudo' },
    ];

    await analisar(tarefas, { report: relatorio({}) });

    const urls = api.get.mock.calls.map(([url]) => url);
    expect(urls.some((u) => u.includes('/timer'))).toBe(false);
    expect(urls.some((u) => u.includes('/focus-sessions'))).toBe(false);
  });

  it('mantém agendado, estimado e executado como séries separadas', async () => {
    const result = await analisar([], {
      // Segunda: 2h estimadas nas tarefas e 1h executada.
      report: relatorio({ 0: { estimatedMinutes: 120, focusSeconds: 3600 } }),
      // Segunda: um bloco de 9h às 10h30 no calendário (1h30 agendada).
      blocks: [bloco(0, 9, 10), { ...bloco(0, 14, 15), id: 'extra' }],
    });

    const segunda = result.current.desvio[0];
    expect(segunda.estimado).toBe(2);
    expect(segunda.executado).toBe(1);
    expect(segunda.agendado).toBe(2); // dois blocos de 1h

    expect(result.current.totalEstimado).toBe(2);
    expect(result.current.totalExecutado).toBe(1);
    expect(result.current.totalAgendado).toBe(2);
  });

  // O relatório agrega a estimativa por dueDate, então tarefa sem data é
  // invisível para ele. Se o painel confiasse só nesse total, o usuário definia
  // o tempo de uma tarefa sem data e nada mudava na tela.
  it('soma no total a estimativa de tarefa sem data, que o relatório não enxerga', async () => {
    const result = await analisar([
      { id: 'a', dataIso: diaDaSemana(0), duracaoMin: 120, done: false, cat: 'Estudo' },
      { id: 'b', dataIso: null, duracaoMin: 180, done: false, cat: 'Estudo' },
    ], { report: relatorio({ 0: { estimatedMinutes: 120 } }) });

    expect(result.current.estimadoSemData).toBe(3);
    expect(result.current.tarefasSemData).toBe(1);
    expect(result.current.totalEstimado).toBe(5); // 2h com data + 3h sem data
  });

  it('conta tarefas sem data mesmo sem estimativa e independentemente do status', async () => {
    const result = await analisar([
      { id: 'b', dataIso: null, duracaoMin: null, done: false, cat: 'Estudo' },
      { id: 'c', dataIso: null, duracaoMin: null, done: true, cat: 'Estudo' },
    ], { report: relatorio({}) });

    expect(result.current.estimadoSemData).toBe(0);
    expect(result.current.tarefasSemData).toBe(2);
    expect(result.current.tarefasSemDataComEstimativa).toBe(0);
  });

  it('mantém a tarefa sem data fora das séries por dia, que não têm onde pendurá-la', async () => {
    const result = await analisar([
      { id: 'b', dataIso: null, duracaoMin: 180, done: false, cat: 'Estudo' },
    ], { report: relatorio({}) });

    expect(result.current.desvio.every((d) => d.estimado === 0)).toBe(true);
    // Mas o gráfico não pode dizer "semana vazia" quando há tempo estimado.
    expect(result.current.temDados).toBe(true);
  });

  it('inclui a tarefa sem data na distribuição por categoria', async () => {
    const result = await analisar([
      { id: 'b', dataIso: null, duracaoMin: 180, done: false, cat: 'Estudo', catCor: '#0f0' },
    ], { report: relatorio({}) });

    expect(result.current.categorias).toEqual([{ nome: 'Estudo', cor: '#0f0', horas: 3 }]);
  });

  it('põe o executado no dia trabalhado, não no vencimento da tarefa', async () => {
    // A tarefa vence na segunda; o trabalho aconteceu na quarta.
    const result = await analisar([], {
      report: relatorio({
        0: { estimatedMinutes: 120 },
        2: { focusSeconds: 3600 },
      }),
    });

    const { desvio } = result.current;
    expect(desvio[0].estimado).toBe(2);
    expect(desvio[0].executado).toBe(0);
    expect(desvio[2].executado).toBe(1);
    expect(desvio[2].estimado).toBe(0);
  });

  it('soma Pomodoro e cronômetro no tempo de hoje, mantendo a origem separável', async () => {
    const diaNativo = new Date().getDay();
    const hoje = diaNativo === 0 ? 6 : diaNativo - 1;

    const result = await analisar([], {
      report: relatorio({ [hoje]: { focusSeconds: 3000, focusSessions: 2, timerSeconds: 1800 } }),
    });

    expect(result.current.hoje).toEqual({
      minutos: 80,             // 50 de foco + 30 de cronômetro
      focoMinutos: 50,
      cronometroMinutos: 30,
      ciclos: 2,
    });
  });

  it('não repete tarefas sem data ao consultar uma semana histórica', async () => {
    responder({ report: relatorio({}) });
    const historica = new Date(semana.inicio);
    historica.setDate(historica.getDate() - 7);
    const { result } = renderHook(
      () => useAnaliseSemanal([
        { id: 'sem-data', dataIso: null, duracaoMin: 120, done: false, cat: 'Estudo' },
      ], historica),
      { wrapper: criarWrapper() },
    );

    await waitFor(() => expect(result.current.carregando).toBe(false));
    expect(result.current.tarefasSemData).toBe(0);
    expect(result.current.estimadoSemData).toBe(0);
  });

  it('usa categorias e conclusão congeladas numa semana fechada', async () => {
    const report = {
      ...relatorio({ 0: { estimatedMinutes: 600 } }),
      totalTasks: 7,
      dueTasksCompleted: 5,
      totalEstimatedMinutes: 600,
      byCategory: [{
        categoryId: 'cat-1', categoryName: 'Genérico', categoryColor: '#0aa',
        estimatedMinutes: 600, measuredSeconds: 0, inferredSeconds: 0,
        dueTasks: 7, dueTasksCompleted: 5,
      }],
    };
    const result = await analisar([], { report, status: 'CLOSED', source: 'SNAPSHOT' });

    expect(result.current.fechada).toBe(true);
    expect(result.current.fonte).toBe('SNAPSHOT');
    expect(result.current.conclusao).toEqual({ feitas: 5, total: 7 });
    expect(result.current.categorias).toEqual([{ nome: 'Genérico', cor: '#0aa', horas: 10 }]);
    expect(result.current.execucaoPorCategoria[0]).toMatchObject({
      total: 7, concluidas: 5, pendentes: 2, somenteResumo: true,
    });
  });

  it('busca a visão geral em uma chamada ao schedule-service e soma os relatórios recebidos', async () => {
    const inicio = new Date();
    inicio.setDate(inicio.getDate() - 100);
    api.get.mockImplementation((url) => {
      if (url.includes('/analytics/overall')) {
        const parcial = {
          ...relatorio({}),
          totalTasks: 1,
          dueTasksCompleted: 1,
          totalEstimatedMinutes: 60,
        };
        return Promise.resolve({ reports: [parcial, parcial], timeBlocks: [] });
      }
      return Promise.reject(new Error(`URL inesperada: ${url}`));
    });

    const { result } = renderHook(
      () => useAnaliseSemanal([], undefined, { geral: true, inicio }),
      { wrapper: criarWrapper() },
    );
    await waitFor(() => expect(result.current.carregando).toBe(false));

    const consultas = api.get.mock.calls.filter(([url]) => url.includes('/analytics/overall'));
    expect(consultas).toHaveLength(1);
    expect(result.current.totalEstimado).toBe(2);
    expect(result.current.conclusao).toEqual({ feitas: 2, total: 2 });
  });

  it('conta o tempo do cronômetro no executado do dia', async () => {
    // Sem sessão de foco nenhuma: antes o dia apareceria zerado.
    const result = await analisar([], { report: relatorio({ 3: { timerSeconds: 5400 } }) });

    expect(result.current.desvio[3].executado).toBe(1.5);
    expect(result.current.totalExecutado).toBe(1.5);
  });

  it('conta a conclusão pelas tarefas da semana, não pelo relatório', async () => {
    const tarefas = [
      { id: 'a', dataIso: diaDaSemana(0), duracaoMin: 60, done: true, cat: 'Estudo' },
      { id: 'b', dataIso: diaDaSemana(1), duracaoMin: 60, done: false, cat: 'Estudo' },
      { id: 'c', dataIso: '2020-01-01', duracaoMin: 60, done: true, cat: 'Estudo' }, // fora da semana
    ];

    // O relatório diz 9 concluídas (inclui atrasadas de outras semanas); a tela
    // promete "tarefas desta semana", então o anel tem de mostrar 1 de 2.
    const result = await analisar(tarefas, {
      report: { ...relatorio({}), totalTasks: 2, completedTasks: 9 },
    });

    expect(result.current.conclusao).toEqual({ feitas: 1, total: 2 });
  });

  it('expõe o erro em vez de fingir semana vazia quando o relatório falha', async () => {
    // Sem isto, um serviço fora do ar produz zeros e a tela fica idêntica a uma
    // semana sem nada — o usuário não tem como saber que algo quebrou.
    api.get.mockRejectedValue(new ApiError('Erro 404', 404));

    const { result } = renderHook(() => useAnaliseSemanal([]), { wrapper: criarWrapper() });
    await waitFor(() => expect(result.current.erro).toBeTruthy());

    expect(result.current.erro.status).toBe(404);
    expect(result.current.temDados).toBe(false);
  });

  it('propaga falha das consultas-base e recarrega todas as fontes', async () => {
    responder({ report: relatorio({}) });
    const erroCategorias = new ApiError('Categorias indisponíveis', 503);
    const recarregarBase = vi.fn();
    const { result } = renderHook(
      () => useAnaliseSemanal([], undefined, { erroBase: erroCategorias, recarregarBase }),
      { wrapper: criarWrapper() },
    );

    await waitFor(() => expect(result.current.carregando).toBe(false));
    expect(result.current.erro).toBe(erroCategorias);

    result.current.recarregar();
    expect(recarregarBase).toHaveBeenCalledTimes(1);
  });

  it('reconhece que há dados quando só existe agenda, sem tarefa com data', async () => {
    const result = await analisar([], { report: relatorio({}), blocks: [bloco(1, 8, 10)] });

    expect(result.current.temTarefas).toBe(false);
    expect(result.current.temDados).toBe(true);
    expect(result.current.desvio[1].agendado).toBe(2);
  });
});
