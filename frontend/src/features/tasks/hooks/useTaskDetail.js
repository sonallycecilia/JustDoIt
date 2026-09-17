// Dados dos módulos do detalhe da tarefa — TUDO persistido no task-service.
// (No front antigo, subtarefas/nota/módulos/ciclo/timer viviam só em
// localStorage e nunca chegavam ao backend.)
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api, getOuNull } from '@/api/client';
import { endpoints } from '@/api/endpoints';
import { invalidarMetricas } from '@/features/tasks/hooks/useTasks';

// ─── Subtarefas ───────────────────────────────────────────────────────────────
export function useSubtarefas(taskId) {
  return useQuery({
    queryKey: ['subtarefas', taskId],
    queryFn: async () => {
      const lista = await api.get(endpoints.tasks.subtasks.list(taskId));
      return (Array.isArray(lista) ? lista : []).map((s) => ({
        id: s.id,
        titulo: s.title,
        done: s.status === 'COMPLETED',
        position: s.position,
      }));
    },
    enabled: Boolean(taskId),
  });
}

export function useCriarSubtarefa(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ titulo, position }) =>
      api.post(endpoints.tasks.subtasks.create(taskId), { title: titulo, position }),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['subtarefas', taskId] }),
  });
}

export function useToggleSubtarefa(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (subId) => api.patch(endpoints.tasks.subtasks.toggle(taskId, subId)),
    onMutate: async (subId) => {
      await qc.cancelQueries({ queryKey: ['subtarefas', taskId] });
      const anterior = qc.getQueryData(['subtarefas', taskId]);
      qc.setQueryData(['subtarefas', taskId], (subs) =>
        (subs || []).map((s) => (s.id === subId ? { ...s, done: !s.done } : s)));
      return { anterior };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.anterior) qc.setQueryData(['subtarefas', taskId], ctx.anterior);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['subtarefas', taskId] }),
  });
}

export function useRemoverSubtarefa(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (subId) => api.remove(endpoints.tasks.subtasks.remove(taskId, subId)),
    onMutate: async (subId) => {
      await qc.cancelQueries({ queryKey: ['subtarefas', taskId] });
      const anterior = qc.getQueryData(['subtarefas', taskId]);
      qc.setQueryData(['subtarefas', taskId], (subs) => (subs || []).filter((s) => s.id !== subId));
      return { anterior };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.anterior) qc.setQueryData(['subtarefas', taskId], ctx.anterior);
    },
    onSettled: () => qc.invalidateQueries({ queryKey: ['subtarefas', taskId] }),
  });
}

// ─── Nota ─────────────────────────────────────────────────────────────────────
export function useNota(taskId) {
  return useQuery({
    queryKey: ['nota', taskId],
    queryFn: async () => {
      const nota = await getOuNull(endpoints.tasks.note(taskId));
      return nota?.content ?? '';
    },
    enabled: Boolean(taskId),
  });
}

export function useSalvarNota(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (content) => api.put(endpoints.tasks.note(taskId), { content }),
    onSuccess: (resp) => qc.setQueryData(['nota', taskId], resp?.content ?? ''),
  });
}

// Limpar a nota. O PUT recusa content em branco (@NotBlank no backend), então
// esvaziar a textarea é DELETE — antes o texto simplesmente voltava no reload.
export function useApagarNota(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.remove(endpoints.tasks.note(taskId)),
    onSuccess: () => qc.setQueryData(['nota', taskId], ''),
  });
}

// ─── Configuração de módulos ativos ──────────────────────────────────────────
// O backend guarda 5 flags; "subtarefas" não tem flag própria e é derivado da
// existência de subtarefas (ligado automaticamente quando há alguma).
export const MODULOS_PADRAO = { foco: false, ciclo: false, prioridade: false, tempo: false, notas: false };

export function useModulos(taskId) {
  return useQuery({
    queryKey: ['modulos', taskId],
    queryFn: async () => {
      const cfg = await getOuNull(endpoints.tasks.moduleConfig(taskId));
      if (!cfg) return { ...MODULOS_PADRAO };
      return {
        foco: Boolean(cfg.focusEnabled),
        ciclo: Boolean(cfg.cycleEnabled),
        prioridade: Boolean(cfg.priorityEnabled),
        tempo: Boolean(cfg.timerEnabled),
        notas: Boolean(cfg.notesEnabled),
      };
    },
    enabled: Boolean(taskId),
  });
}

export function useSalvarModulos(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (mods) => api.put(endpoints.tasks.moduleConfig(taskId), {
      focusEnabled: mods.foco,
      cycleEnabled: mods.ciclo,
      priorityEnabled: mods.prioridade,
      timerEnabled: mods.tempo,
      notesEnabled: mods.notas,
    }),
    onMutate: async (mods) => {
      await qc.cancelQueries({ queryKey: ['modulos', taskId] });
      const anterior = qc.getQueryData(['modulos', taskId]);
      qc.setQueryData(['modulos', taskId], mods);
      return { anterior };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.anterior) qc.setQueryData(['modulos', taskId], ctx.anterior);
    },
  });
}

// ─── Recorrência (cycle-config) ───────────────────────────────────────────────
// Modelo da UI: { tipo: 'none'|'DAILY'|…|'custom', custom: {count, unit,
// occurrences, startIso} | null }. Os detalhes do personalizado vêm do próprio
// GET /cycle-config — sem cache espelho local (diferente do app antigo).
function cicloDaApi(cfg) {
  if (!cfg?.cycleType) return { tipo: 'none', custom: null };
  if (cfg.cycleType !== 'CUSTOM') return { tipo: cfg.cycleType, custom: null };
  return {
    tipo: 'custom',
    custom: {
      count: cfg.intervalCount ?? 1,
      unit: cfg.intervalUnit === 'DAYS' ? 'dias' : 'horas',
      occurrences: cfg.totalOccurrences ?? 2,
      startIso: cfg.startDate || null,
    },
  };
}

// UI → CycleConfigRequest. `valor` = 'none' | tipo do enum | { tipo:'custom',
// custom, startTime } (startTime "HH:mm" só quando a unidade é horas).
export function cicloParaApi(valor) {
  if (typeof valor === 'string') return { cycleType: valor };
  const c = valor.custom;
  return {
    cycleType: 'CUSTOM',
    intervalUnit: c.unit === 'dias' ? 'DAYS' : 'HOURS',
    intervalCount: Number(c.count) || 1,
    totalOccurrences: Number(c.occurrences) || 2,
    startDate: c.startIso || null,
    startTime: c.unit === 'horas' ? (valor.startTime || null) : null,
  };
}

export function useCiclo(taskId) {
  return useQuery({
    queryKey: ['ciclo', taskId],
    queryFn: async () => cicloDaApi(await getOuNull(endpoints.tasks.cycleConfig(taskId))),
    enabled: Boolean(taskId),
  });
}

export function useSalvarCiclo(taskId) {
  const qc = useQueryClient();
  return useMutation({
    // 'none' = remover recorrência → DELETE; qualquer tipo → PUT (upsert)
    mutationFn: (valor) => (valor === 'none'
      ? api.remove(endpoints.tasks.cycleConfig(taskId))
      : api.put(endpoints.tasks.cycleConfig(taskId), cicloParaApi(valor))),
    onMutate: async (valor) => {
      await qc.cancelQueries({ queryKey: ['ciclo', taskId] });
      const anterior = qc.getQueryData(['ciclo', taskId]);
      const novo = valor === 'none' ? { tipo: 'none', custom: null }
        : typeof valor === 'string' ? { tipo: valor, custom: null }
        : { tipo: 'custom', custom: valor.custom };
      qc.setQueryData(['ciclo', taskId], novo);
      return { anterior };
    },
    onError: (_e, _v, ctx) => {
      if (ctx?.anterior !== undefined) qc.setQueryData(['ciclo', taskId], ctx.anterior);
    },
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['ciclo', taskId] });
      // O PUT materializa as próximas ocorrências no backend. Sem atualizar a
      // lista, elas só apareciam depois de recarregar a página.
      qc.invalidateQueries({ queryKey: ['tarefas'] });
    },
  });
}

// ─── Cronômetro de execução + tempo estimado (timer) ─────────────────────────
// O recurso /timer guarda os dois: actualSeconds (cronômetro) e estimatedMinutes (duração estimada).
// O PUT faz merge parcial: enviar só um campo não zera o outro (verificado).
export function useTimer(taskId) {
  return useQuery({
    queryKey: ['timer', taskId],
    queryFn: async () => {
      const t = await getOuNull(endpoints.tasks.timer(taskId));
      return {
        segundos: Number(t?.actualSeconds ?? 0),
        estimadoMin: t?.estimatedMinutes != null ? Number(t.estimatedMinutes) : null,
      };
    },
    enabled: Boolean(taskId),
  });
}

// O início do cronômetro vive no servidor. Assim, fechar a aba ou perder a rede
// não perde o intervalo: ao voltar, a UI recupera taskId + startedAt e continua
// exibindo o tempo derivado do relógio, sem depender de setInterval ter rodado.
export function useCronometroAtivo(enabled = true) {
  return useQuery({
    queryKey: ['timer-active'],
    queryFn: () => getOuNull(endpoints.tasks.activeTimer),
    enabled,
    refetchOnMount: 'always',
    refetchOnWindowFocus: true,
  });
}

export function useIniciarCronometro(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post(endpoints.tasks.timerStart(taskId)),
    onSuccess: (ativo) => qc.setQueryData(['timer-active'], ativo),
  });
}

export function usePararCronometro(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.post(endpoints.tasks.timerStop(taskId)),
    onSuccess: (timer) => {
      qc.setQueryData(['timer-active'], null);
      if (timer?.actualSeconds != null) {
        qc.setQueryData(['timer', taskId], (atual) => ({
          ...(atual || {}),
          segundos: Number(timer.actualSeconds),
        }));
      }
      invalidarMetricas(qc);
    },
  });
}

// Soma segundos rodados (PATCH /timer/log) — chamado ao pausar/sair da página.
export function useLogarTempo(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (seconds) => api.patch(endpoints.tasks.timerLog(taskId), { seconds }),
    onSuccess: (resp) => {
      if (resp?.actualSeconds != null) {
        qc.setQueryData(['timer', taskId], (t) => ({ ...(t || {}), segundos: Number(resp.actualSeconds) }));
      }
      invalidarMetricas(qc);
    },
  });
}

// Zerar o total acumulado (PUT /timer com actualSeconds: 0).
export function useZerarTempo(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: () => api.put(endpoints.tasks.timer(taskId), { actualSeconds: 0 }),
    onSuccess: () => {
      qc.setQueryData(['timer', taskId], (t) => ({ ...(t || {}), segundos: 0 }));
      invalidarMetricas(qc);
    },
  });
}

// Duração estimada (PUT /timer com estimatedMinutes). NUNCA vai no TaskRequest —
// o backend descarta o campo lá; o recurso é o /timer.
export function useSalvarTempoEstimado(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (estimatedMinutes) => api.put(endpoints.tasks.timer(taskId), { estimatedMinutes }),
    onSuccess: (resp) => {
      if (resp?.estimatedMinutes !== undefined) {
        qc.setQueryData(['timer', taskId], (t) => ({ ...(t || {}), estimadoMin: resp.estimatedMinutes != null ? Number(resp.estimatedMinutes) : null }));
      }
      // A lista de tarefas expõe duracaoMin (via estimatedMinutes) p/ o calendário.
      qc.invalidateQueries({ queryKey: ['tarefas'] });
      // E a estimativa é o "estimado" do relatório da semana.
      invalidarMetricas(qc);
    },
  });
}

// ─── Sessões de foco (Pomodoro) ───────────────────────────────────────────────
export function useSessoesFoco(taskId) {
  return useQuery({
    queryKey: ['foco', taskId],
    queryFn: async () => {
      const lista = await getOuNull(endpoints.tasks.focusSessions(taskId));
      return Array.isArray(lista) ? lista : [];
    },
    enabled: Boolean(taskId),
  });
}

// Registra um ciclo de foco concluído (POST /focus-sessions já completo).
export function useRegistrarCicloFoco(taskId) {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ focusMinutes, startedAt, endedAt }) =>
      api.post(endpoints.tasks.focusSessions(taskId), {
        focusMinutes,
        sessionType: 'FOCUS',
        startedAt,
        endedAt,
        completed: true,
      }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['foco', taskId] });
      invalidarMetricas(qc);
    },
  });
}
