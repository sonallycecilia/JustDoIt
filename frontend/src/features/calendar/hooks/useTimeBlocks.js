// Blocos de tempo do calendário (schedule-service, /time-blocks).
//
// O backend só guarda quando e quanto: taskId / startDateTime / endDateTime /
// estimatedMinutes / date — tudo em data/hora absoluta. Os campos visuais
// (título, categoria, prioridade, concluído) vêm da tarefa vinculada (taskId),
// resolvida pela UI. Aqui só traduzimos posição ⇄ data/hora.
//
// O modelo da UI usa hora decimal (8.5 = 08:30) e data ISO (YYYY-MM-DD).
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';

function pad(n) { return String(n).padStart(2, '0'); }

// 8.5 → "08:30"
export function fmtHora(x) {
  const h = Math.floor(x);
  const m = Math.round((x % 1) * 60);
  return `${pad(h)}:${pad(m)}`;
}

// "2026-06-10T08:30:00" → 8.5
function horaDeIso(dataHora) {
  const parte = String(dataHora).split('T')[1] || '00:00';
  const [hh, mm] = parte.split(':').map(Number);
  return (hh || 0) + (mm || 0) / 60;
}

// evento da UI ({iso, ini, fim, taskId}) → TimeBlockRequest
export function blocoParaApi(ev) {
  return {
    taskId: ev.taskId || null,
    startDateTime: `${ev.iso}T${fmtHora(ev.ini)}:00`,
    endDateTime: `${ev.iso}T${fmtHora(ev.fim)}:00`,
    estimatedMinutes: Math.round((ev.fim - ev.ini) * 60),
    date: ev.iso,
  };
}

// TimeBlockResponse → bloco cru da UI (sem título/categoria — a UI completa
// a partir da tarefa vinculada)
export function blocoDaApi(b) {
  return {
    id: b.id,
    taskId: b.taskId || null,
    iso: b.date,
    ini: horaDeIso(b.startDateTime),
    fim: horaDeIso(b.endDateTime),
  };
}

// Blocos crus de um intervalo de datas (GET /time-blocks?from=&to=).
export function useBlocos(from, to) {
  return useQuery({
    queryKey: ['blocos', from, to],
    queryFn: () => api.get(endpoints.timeBlocks.range(from, to)),
    select: (dados) => (Array.isArray(dados) ? dados : []).map(blocoDaApi),
    enabled: Boolean(from && to),
  });
}

// As escritas são otimistas na UI do calendário (estado local `eventos`);
// aqui só persistimos e invalidamos por prefixo para o refetch trazer a
// verdade do servidor.
export function useCriarBloco() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ev) => api.post(endpoints.timeBlocks.create, blocoParaApi(ev)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['blocos'] });
      qc.invalidateQueries({ queryKey: ['analytics-week'] });
      qc.invalidateQueries({ queryKey: ['analytics-overall'] });
    },
  });
}

export function useAtualizarBloco() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (ev) => api.put(endpoints.timeBlocks.update(ev.id), blocoParaApi(ev)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['blocos'] });
      qc.invalidateQueries({ queryKey: ['analytics-week'] });
      qc.invalidateQueries({ queryKey: ['analytics-overall'] });
    },
  });
}

export function useRemoverBloco() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id) => api.remove(endpoints.timeBlocks.remove(id)),
    onSettled: () => {
      qc.invalidateQueries({ queryKey: ['blocos'] });
      qc.invalidateQueries({ queryKey: ['analytics-week'] });
      qc.invalidateQueries({ queryKey: ['analytics-overall'] });
    },
  });
}

// ─── Sincronização bloco ⇄ tarefa ────────────────────────────────────────────
// Mantém dueDate/dueTime (e categoria/prioridade, no modal) da tarefa em
// sincronia com o calendário. O corpo do PUT /tasks é montado a partir do
// TaskResponse CRU já em cache — assim nenhum campo é zerado no backend.
// `patch` usa os nomes de campo da API (dueDate, dueTime, categoryId, priority);
// dueTime: null zera a hora ("Sem horário").
export function usePatchTarefa() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: ({ taskId, patch }) => {
      const crua = (qc.getQueryData(['tarefas']) || []).find((t) => t.id === taskId);
      if (!crua) return Promise.resolve(null);
      return api.put(endpoints.tasks.update(taskId), {
        title: crua.title,
        description: crua.description || null,
        categoryId: crua.categoryId || null,
        priority: crua.priority,
        dueDate: crua.dueDate || null,
        dueTime: crua.dueTime || null,
        ...patch,
      });
    },
    // Otimista no cache cru: abrir o detalhe logo após arrastar já mostra a
    // nova data/hora, sem esperar o PUT responder.
    onMutate: async ({ taskId, patch }) => {
      await qc.cancelQueries({ queryKey: ['tarefas'] });
      const anterior = qc.getQueryData(['tarefas']);
      qc.setQueryData(['tarefas'], (lista) =>
        (lista || []).map((t) => (t.id === taskId ? { ...t, ...patch } : t)));
      return { anterior };
    },
    onError: (_e, _v, ctx) => { if (ctx?.anterior) qc.setQueryData(['tarefas'], ctx.anterior); },
    onSettled: () => qc.invalidateQueries({ queryKey: ['tarefas'] }),
  });
}
