// Análise semanal derivada de dados REAIS do backend.
//
// A tela semanal usa um único contrato do schedule-service. Para uma semana
// aberta ele reúne dados atuais; no fechamento ele devolve o mesmo payload
// congelado, impedindo edições futuras de reescrever o histórico.
//
// São TRÊS grandezas distintas por dia, e nenhuma substitui a outra:
//   • AGENDADO  → blocos que o usuário pôs no calendário (schedule-service,
//     GET /time-blocks?from=&to=). É o compromisso com horário marcado.
//   • ESTIMADO  → soma das estimativas das tarefas que vencem no dia
//     (task-service, GET /tasks/report). Uma tarefa pode ter estimativa sem
//     nunca ter entrado na agenda.
//   • EXECUTADO → tempo trabalhado de fato (mesmo relatório), somando as duas
//     formas de registrar trabalho: ciclos de Pomodoro e intervalos do
//     cronômetro. Cada um cai no dia em que COMEÇOU.
//
// É uma requisição semanal. O modo geral continua paginando relatórios de até
// 92 dias pelo endpoint agregado já existente.
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { blocoDaApi } from '@/features/calendar/hooks/useTimeBlocks';
import { dataIso, deIso, intervaloSemana } from '@/lib/utils';

export const DIAS_SEMANA = ['Seg', 'Ter', 'Qua', 'Qui', 'Sex', 'Sáb', 'Dom'];

// Índice 0–6 com a semana começando na segunda, como o weekly-plan.
function indiceDia(data) {
  return data.getDay() === 0 ? 6 : data.getDay() - 1;
}

function somarCategorias(relatorios) {
  const mapa = new Map();
  relatorios.flatMap((r) => r.byCategory || []).forEach((c) => {
    const chave = c.categoryId || c.categoryName || 'generico';
    const atual = mapa.get(chave) || { ...c, measuredSeconds: 0, inferredSeconds: 0 };
    atual.measuredSeconds += c.measuredSeconds || 0;
    atual.inferredSeconds += c.inferredSeconds || 0;
    mapa.set(chave, atual);
  });
  return [...mapa.values()];
}

function combinarRelatorios(relatorios) {
  return {
    byDay: relatorios.flatMap((r) => r.byDay || []),
    byCategory: somarCategorias(relatorios),
    taskPerformance: relatorios.flatMap((r) => r.taskPerformance || []),
    totalTasks: relatorios.reduce((s, r) => s + (r.totalTasks || 0), 0),
    dueTasksCompleted: relatorios.reduce((s, r) => s + (r.dueTasksCompleted || 0), 0),
    totalEstimatedMinutes: relatorios.reduce((s, r) => s + (r.totalEstimatedMinutes || 0), 0),
    totalActualSeconds: relatorios.reduce((s, r) => s + (r.totalActualSeconds || 0), 0),
    totalMeasuredSeconds: relatorios.reduce((s, r) => s + (r.totalMeasuredSeconds || 0), 0),
    totalInferredSeconds: relatorios.reduce((s, r) => s + (r.totalInferredSeconds || 0), 0),
    completedInPeriod: relatorios.reduce((s, r) => s + (r.completedInPeriod || 0), 0),
  };
}

export function useAnaliseSemanal(tarefas, dataBase, opcoes = {}) {
  const geral = Boolean(opcoes.geral);
  const semana = intervaloSemana(dataBase);
  const semanaAtual = intervaloSemana();
  const ehSemanaAtual = semana.inicioIso === semanaAtual.inicioIso;
  const inicioGeral = opcoes.inicio ? new Date(opcoes.inicio) : semanaAtual.inicio;
  inicioGeral.setHours(0, 0, 0, 0);
  const fimGeral = new Date();
  fimGeral.setHours(0, 0, 0, 0);
  const inicioGeralIso = dataIso(inicioGeral);
  const fimGeralIso = dataIso(fimGeral);

  const analyticsSemana = useQuery({
    queryKey: ['analytics-week', semana.inicioIso],
    queryFn: () => api.get(endpoints.analytics.week(semana.inicioIso)),
    staleTime: 60_000,
    enabled: !geral,
  });

  const analyticsGeral = useQuery({
    queryKey: ['analytics-overall', inicioGeralIso, fimGeralIso],
    queryFn: () => api.get(endpoints.analytics.overall(inicioGeralIso, fimGeralIso)),
    staleTime: 60_000,
    enabled: geral,
  });

  const relatorioAtual = geral
    ? combinarRelatorios(analyticsGeral.data?.reports || [])
    : analyticsSemana.data?.report;
  const blocosAtuais = geral
    ? (analyticsGeral.data?.timeBlocks || []).map(blocoDaApi)
    : (analyticsSemana.data?.timeBlocks || []).map(blocoDaApi);
  const fonte = geral ? 'LIVE' : (analyticsSemana.data?.source || 'LIVE');
  const dataStatus = geral ? 'COMPLETE' : (analyticsSemana.data?.dataStatus || 'COMPLETE');
  const fechada = !geral && analyticsSemana.data?.status === 'CLOSED';
  const inicioPeriodo = geral ? inicioGeral : semana.inicio;
  const fimPeriodo = geral ? fimGeral : semana.fim;

  const daSemana = (tarefas || []).filter((t) => {
    if (!t.dataIso) return false;
    const d = deIso(t.dataIso);
    return d >= inicioPeriodo && d <= fimPeriodo;
  });

  // Tarefa com tempo estimado e SEM data. O relatório do backend agrega por
  // dueDate, então essas nunca entravam em conta nenhuma: o usuário definia o
  // tempo, olhava o painel e não via nada mudar. Elas contam no TOTAL (o tempo
  // que ele planeja gastar existe), mas ficam fora das séries por dia — não há
  // dia onde pendurá-las, e inventar um mentiria no gráfico.
  // A quantidade exibida no resumo inclui TODAS as tarefas sem data, tenham
  // estimativa ou não e estejam concluídas ou pendentes. Para os cálculos de
  // tempo, porém, usamos apenas o subconjunto que realmente tem estimativa.
  const tarefasSemData = (geral || ehSemanaAtual) ? (tarefas || []).filter((t) => !t.dataIso) : [];
  const semDataComEstimativa = tarefasSemData.filter((t) => t.duracaoMin > 0);

  const porDia = relatorioAtual?.byDay || [];

  // ── As três séries, por dia da semana ──
  const desvio = DIAS_SEMANA.map((dia) => ({ dia, agendado: 0, estimado: 0, executado: 0 }));

  porDia.forEach((d) => {
    const i = indiceDia(deIso(d.date));
    if (!desvio[i]) return;
    desvio[i].estimado += (d.estimatedMinutes || 0) / 60;
    desvio[i].executado += (d.actualSeconds || 0) / 3600;
  });

  // fim - ini é a duração do bloco em horas: é exatamente o que blocoParaApi
  // grava em estimatedMinutes, então bate com o totalScheduledMinutes do resumo.
  blocosAtuais.forEach((b) => {
    const i = indiceDia(deIso(b.iso));
    if (desvio[i]) desvio[i].agendado += Math.max(0, b.fim - b.ini);
  });

  // ── Tempo estimado por categoria ──
  // Semanas abertas mantêm os itens locais clicáveis; semanas fechadas usam o
  // agregado congelado para não depender das tarefas que já foram alteradas.
  const porCat = new Map();
  [...daSemana, ...semDataComEstimativa].forEach((t) => {
    if (!t.duracaoMin) return;
    const atual = porCat.get(t.cat) || { nome: t.cat, cor: t.catCor, horas: 0 };
    atual.horas += t.duracaoMin / 60;
    porCat.set(t.cat, atual);
  });
  const categoriasLocais = [...porCat.values()]
    .map((c) => ({ ...c, horas: Math.round(c.horas * 10) / 10 }))
    .filter((c) => c.horas > 0)
    .sort((a, b) => b.horas - a.horas);
  const categoriasCongeladas = (relatorioAtual?.byCategory || [])
    .map((c) => ({
      nome: c.categoryName,
      cor: c.categoryColor || '#94a3b8',
      horas: Math.round(((c.estimatedMinutes || 0) / 60) * 10) / 10,
    }))
    .filter((c) => c.horas > 0)
    .sort((a, b) => b.horas - a.horas);
  const categorias = fechada ? categoriasCongeladas : categoriasLocais;

  // Execução por categoria usa a coorte correta da semana: tarefas cujo
  // vencimento cai no período. Mantemos os itens, não apenas as contagens, para
  // a tela explicar exatamente quais foram concluídos e quais seguem pendentes.
  const execucaoPorCategoriaMap = new Map();
  daSemana.forEach((t) => {
    const chave = t.categoriaId || t.cat || 'generico';
    const atual = execucaoPorCategoriaMap.get(chave) || {
      id: chave,
      nome: t.cat || 'Sem categoria',
      cor: t.catCor || '#94a3b8',
      tarefas: [],
    };
    atual.tarefas.push({ id: t.id, titulo: t.titulo, concluida: t.done });
    execucaoPorCategoriaMap.set(chave, atual);
  });
  const execucaoPorCategoriaLocal = [...execucaoPorCategoriaMap.values()]
    .map((categoria) => ({
      ...categoria,
      total: categoria.tarefas.length,
      concluidas: categoria.tarefas.filter((t) => t.concluida).length,
      pendentes: categoria.tarefas.filter((t) => !t.concluida).length,
      tarefas: categoria.tarefas.sort((a, b) => Number(a.concluida) - Number(b.concluida)),
    }))
    .sort((a, b) => b.total - a.total || a.nome.localeCompare(b.nome));
  const execucaoPorCategoriaCongelada = (relatorioAtual?.byCategory || [])
    .filter((c) => (c.dueTasks || 0) > 0)
    .map((c) => ({
      id: c.categoryId || c.categoryName || 'generico',
      nome: c.categoryName || 'Sem categoria',
      cor: c.categoryColor || '#94a3b8',
      total: c.dueTasks || 0,
      concluidas: c.dueTasksCompleted || 0,
      pendentes: Math.max(0, (c.dueTasks || 0) - (c.dueTasksCompleted || 0)),
      tarefas: [],
      somenteResumo: true,
    }))
    .sort((a, b) => b.total - a.total || a.nome.localeCompare(b.nome));
  const execucaoPorCategoria = fechada
    ? execucaoPorCategoriaCongelada
    : execucaoPorCategoriaLocal;

  const categoriasExecutadas = (relatorioAtual?.byCategory || [])
    .map((c) => ({
      nome: c.categoryName,
      cor: c.categoryColor || '#94a3b8',
      horas: Math.round((((c.measuredSeconds || 0) + (c.inferredSeconds || 0)) / 3600) * 10) / 10,
    }))
    .filter((c) => c.horas > 0)
    .sort((a, b) => b.horas - a.horas);

  const maioresDesvios = (relatorioAtual?.taskPerformance || []).map((t) => ({
    id: t.taskId,
    titulo: t.title,
    estimado: (t.estimatedMinutes || 0) / 60,
    executado: (t.actualSeconds || 0) / 3600,
    desvio: (t.deviationSeconds || 0) / 3600,
  }));

  // ── Tempo de hoje: o dia corrente dentro do relatório da semana ──
  // Vem quebrado por origem porque a tela precisa dizer de onde veio: "2 ciclos
  // de Pomodoro" seria mentira se o tempo tivesse saído do cronômetro.
  const hojeIso = dataIso(new Date());
  const hoje = porDia.find((d) => d.date === hojeIso);

  const relatorioComCoortes = relatorioAtual?.dueTasksCompleted != null;
  const conclusao = {
    feitas: relatorioComCoortes
      ? relatorioAtual.dueTasksCompleted
      : daSemana.filter((t) => t.done).length,
    total: relatorioComCoortes ? relatorioAtual.totalTasks : daSemana.length,
  };

  const totalAgendado = desvio.reduce((s, d) => s + d.agendado, 0);
  // O agregado do backend continua mandando no que ele consegue ver (tarefa com
  // data, somada por dueDate); o cliente só ACRESCENTA o que aquela consulta
  // descarta por construção, que são as sem data. Os dois conjuntos são
  // disjuntos, então não há risco de contar a mesma tarefa duas vezes, e a
  // agregação não fica duplicada aqui.
  const estimadoSemData = semDataComEstimativa.reduce((s, t) => s + (t.duracaoMin || 0), 0) / 60;
  const totalEstimado = (relatorioAtual?.totalEstimatedMinutes || 0) / 60 + estimadoSemData;
  const totalExecutado = (relatorioAtual?.totalActualSeconds || 0) / 3600;
  const totalMedido = (relatorioAtual?.totalMeasuredSeconds ?? relatorioAtual?.totalActualSeconds ?? 0) / 3600;
  const totalInferido = (relatorioAtual?.totalInferredSeconds || 0) / 3600;
  const consultas = geral ? [analyticsGeral] : [analyticsSemana];

  return {
    semana,
    geral,
    fechada,
    fonte,
    dataStatus,
    conclusao,
    desvio,
    categorias,
    execucaoPorCategoria,
    categoriasExecutadas,
    maioresDesvios,
    totalAgendado,
    totalEstimado,
    // Quanto do estimado veio de tarefa sem data. A tela precisa disso para
    // explicar por que o total não bate com a soma das barras do gráfico.
    estimadoSemData,
    tarefasSemData: tarefasSemData.length,
    tarefasSemDataComEstimativa: semDataComEstimativa.length,
    totalExecutado,
    totalMedido,
    totalInferido,
    coberturaPlanejamento: totalEstimado > 0 ? totalAgendado / totalEstimado : 0,
    concluidasNoPeriodo: relatorioAtual?.completedInPeriod ?? relatorioAtual?.completedTasks ?? 0,
    atrasadasAbertas: relatorioAtual?.overdueOpenTasks
      ?? (tarefas || []).filter((t) => t.overdue).length,
    hoje: {
      minutos: (hoje?.actualSeconds || 0) / 60,
      focoMinutos: (hoje?.focusSeconds || 0) / 60,
      cronometroMinutos: (hoje?.timerSeconds || 0) / 60,
      ciclos: hoje?.focusSessions || 0,
    },
    temTarefas: conclusao.total > 0,
    // Há o que mostrar no gráfico mesmo sem tarefa com data: blocos na agenda
    // ou tempo executado já bastam.
    temDados: desvio.some((d) => d.agendado || d.estimado || d.executado) || estimadoSemData > 0,
    carregando: consultas.some((q) => q.isLoading),
    // Sem isto, uma requisição que falha produz zeros e a tela fica idêntica a
    // uma semana vazia — o pior tipo de erro, o silencioso.
    // Conta, categorias e tarefas também alimentam os cards locais. Uma falha
    // nelas não pode virar zeros nem deixar dados antigos do cache parecerem
    // atuais, mesmo que /tasks/report tenha respondido.
    erro: opcoes.erroBase || consultas.find((q) => q.error)?.error || null,
    recarregar: () => {
      opcoes.recarregarBase?.();
      consultas.forEach((q) => q.refetch());
    },
  };
}
