import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getOuNull } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { calcQuando, dataRelativa, deIso } from '@/lib/utils';
import { prioridadeDaApi, prioridadeParaApi } from '@/features/tasks/lib/priority';
import { categoriaPorId } from '@/features/categories/hooks/useCategories';

// ─── Mapeamento backend (TaskResponse) ↔ modelo da UI ────────────────────────
// O backend é a fonte da verdade; não há mais "meta cache" em localStorage.
// Prioridade agora viaja no campo priority; categoria resolve por categoryId.
export function tarefaDaApi(t, categorias) {
  const cat = categoriaPorId(categorias, t.categoryId);
  const dataObj = t.dueDate ? deIso(t.dueDate) : null;
  const quando = dataObj ? calcQuando(dataObj) : 'all';
  const concluida = t.status === 'COMPLETED';
  return {
    id: t.id,
    seriesId: t.seriesId || null,
    cycleType: t.cycleType || null,
    titulo: t.title,
    descricao: t.description || '',
    cat: cat.nome,
    catCor: cat.cor,
    categoriaId: t.categoryId || 'generico',
    prioridade: prioridadeDaApi(t.priority),
    done: concluida,
    criadaEm: t.createdAt || null,
    dataIso: t.dueDate || null,
    data: dataObj ? dataRelativa(dataObj) : 'Sem data',
    quando,
    overdue: !concluida && quando === 'past',
    hora: t.dueTime ? String(t.dueTime).slice(0, 5) : undefined,
    lembreteMinutosAntes: t.reminderMinutesBefore ?? null,
    // Duração estimada (minutos) — vem do timer da tarefa; usada pelo
    // calendário para dimensionar blocos criados por arraste.
    duracaoMin: t.estimatedMinutes || null,
  };
}

// modelo da UI → corpo aceito pelo backend (TaskRequest)
export function tarefaParaApi(d) {
  const catId = d.categoriaId && d.categoriaId !== 'generico' ? d.categoriaId : null;
  return {
    title: d.titulo,
    description: d.descricao || null,
    categoryId: catId,
    priority: prioridadeParaApi(d.prioridade),
    dueDate: d.dataIso || null,
    reminderMinutesBefore: d.lembreteMinutosAntes ?? null,
    dueTime: d.hora || null, // "HH:mm" — aceito como LocalTime
  };
}

// ─── Queries ──────────────────────────────────────────────────────────────────
export function useTarefas(categorias) {
  return useQuery({
    queryKey: ['tarefas'],
    queryFn: () => api.get(endpoints.tasks.list),
    // O select roda a cada render com o resultado cacheado: a lista crua fica
    // no cache e o modelo da UI é derivado, já com as categorias resolvidas.
    select: (dados) => (Array.isArray(dados) ? dados : []).map((t) => tarefaDaApi(t, categorias)),
    enabled: Boolean(categorias),
  });
}

export function useTarefa(id, categorias) {
  return useQuery({
    queryKey: ['tarefas', id],
    queryFn: () => api.get(endpoints.tasks.detail(id)),
    select: (t) => (t ? tarefaDaApi(t, categorias) : null),
    enabled: Boolean(id && categorias),
  });
}

// ─── Mutations ────────────────────────────────────────────────────────────────

// A Visão Geral e a Análise leem a semana de GET /tasks/report, com staleTime de
// 60s. Quem mexe em tarefa, estimativa ou tempo TEM de derrubar esse cache: sem
// isso, criar uma tarefa com tempo estimado não mudava número nenhum na tela até
// o minuto vencer, e o app parecia não computar nada.
export function invalidarMetricas(qc) {
  qc.invalidateQueries({ queryKey: ['relatorio'] });
  qc.invalidateQueries({ queryKey: ['analytics-week'] });
  qc.invalidateQueries({ queryKey: ['analytics-overall'] });
}

export function useCriarTarefa() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (dados) => api.post(endpoints.tasks.create, tarefaParaApi(dados)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      invalidarMetricas(qc);
    },
  });
}

export function useAtualizarTarefa() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ id, dados }) => api.put(endpoints.tasks.update(id), tarefaParaApi(dados)),
    // A data de vencimento entra na conta da semana, então editar tarefa também
    // pode mudar o estimado do relatório.
    onSuccess: (_resp, { id }) => {
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      qc.invalidateQueries({ queryKey: ['tarefas', id] });
      invalidarMetricas(qc);
    },
  });
}

// Concluir uma tarefa que nunca foi cronometrada registra a estimativa dela como
// tempo executado. Sem isto, quem toca o dia pela lista (e não pelo Pomodoro nem
// pelo cronômetro) fechava a semana com "0h executadas" no dashboard mesmo tendo
// concluído tudo: as duas únicas fontes de tempo exigiam ter rodado um timer.
//
// Duas guardas impedem inventar número:
//   • sem estimativa não há o que registrar (nada é gravado);
//   • se já existe tempo gravado, ele MANDA. Tempo medido de verdade não é
//     sobrescrito por estimativa, e reabrir/concluir de novo não soma duas vezes.
//
// Vai tudo para o backend: o PATCH /timer/log cria um TimeEntry datado de agora
// (registrarIntervalo, no TaskTimerService) e incrementa o acumulado da tarefa,
// que é exatamente de onde /tasks/report tira o executado da semana.
//
// Retorna se gravou, para o chamador saber se precisa invalidar o relatório.
async function registrarTempoDaConclusao(id) {
  const timer = await getOuNull(endpoints.tasks.timer(id));
  const estimadoMin = Number(timer?.estimatedMinutes || 0);
  if (estimadoMin <= 0) return false;
  if (Number(timer?.actualSeconds || 0) > 0) return false;
  await api.patch(endpoints.tasks.timerLog(id), {
    seconds: estimadoMin * 60,
    source: 'COMPLETION_ESTIMATE',
  });
  return true;
}

// Concluir/reabrir com atualização otimista: a UI responde na hora e o cache
// volta ao estado anterior se o backend recusar.
export function useToggleDone() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ id, concluir }) => {
      const resp = await api.patch(concluir ? endpoints.tasks.complete(id) : endpoints.tasks.reopen(id));
      // O registro de tempo é acessório e fica FORA do resultado da conclusão:
      // se ele falhar, a tarefa continua concluída (e está, no backend) em vez
      // de a tela reverter o check dizendo que nada foi salvo.
      if (concluir) {
        try { await registrarTempoDaConclusao(id); } catch { /* a conclusão já está gravada */ }
      }
      return resp;
    },
    onMutate: async ({ id, concluir }) => {
      await qc.cancelQueries({ queryKey: ['tarefas'] });
      const anterior = qc.getQueryData(['tarefas']);
      qc.setQueryData(['tarefas'], (lista) =>
        (lista || []).map((t) => (t.id === id ? { ...t, status: concluir ? 'COMPLETED' : 'PENDING' } : t)));
      return { anterior };
    },
    onError: (_err, _vars, ctx) => {
      if (ctx?.anterior) qc.setQueryData(['tarefas'], ctx.anterior);
    },
    // O relatório da semana entra aqui porque a conclusão pode ter gerado tempo
    // executado; sem invalidá-lo, o dashboard seguiria mostrando o número antigo
    // até o staleTime de 60s vencer.
    onSettled: (_resp, _err, { id }) => {
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      qc.invalidateQueries({ queryKey: ['timer', id] });
      invalidarMetricas(qc);
    },
  });
}

export function useRemoverTarefa() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (entrada) => {
      const { id, scope = 'INSTANCE' } = typeof entrada === 'string' ? { id: entrada } : entrada;
      return api.remove(endpoints.tasks.remove(id, scope));
    },
    onMutate: async (entrada) => {
      const { id, scope = 'INSTANCE', seriesId = null } =
        typeof entrada === 'string' ? { id: entrada } : entrada;
      await qc.cancelQueries({ queryKey: ['tarefas'] });
      const anterior = qc.getQueryData(['tarefas']);
      qc.setQueryData(['tarefas'], (lista) => (lista || []).filter((t) => {
        if (scope === 'INSTANCE') return t.id !== id;
        const raiz = seriesId || id;
        return t.id !== raiz && t.seriesId !== raiz;
      }));
      return { anterior };
    },
    onError: (_err, _id, ctx) => {
      if (ctx?.anterior) qc.setQueryData(['tarefas'], ctx.anterior);
    },
    onSuccess: (_resp, id) => qc.removeQueries({ queryKey: ['tarefas', id], exact: true }),
    // Excluir tira a estimativa da tarefa do total da semana.
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      invalidarMetricas(qc);
    },
  });
}
