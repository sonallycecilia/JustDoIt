// ─── Configuração dos serviços ────────────────────────────────────────────────
// Em produção todos os serviços são roteados via Nginx no mesmo domínio.
const PROD_API = 'https://justdoitapi.duckdns.org';
const isDev = location.hostname === 'localhost' || location.hostname === '127.0.0.1';

export const SVC = isDev
  ? { auth: 'http://localhost:8080', tasks: 'http://localhost:8081', sched: 'http://localhost:8082', notif: 'http://localhost:8083' }
  : { auth: PROD_API, tasks: PROD_API, sched: PROD_API, notif: PROD_API };

// ─── Endpoints ────────────────────────────────────────────────────────────────
// Caminhos alinhados aos controllers reais do backend (task-service etc.):
//   /tasks/{id}/note, /cycle-config, /module-config, /timer, /focus-sessions.
// (O front antigo apontava para /notes, /cycle, /modules… que não existem.)
export const endpoints = {
  auth: {
    login: `${SVC.auth}/auth/login`,
    register: `${SVC.auth}/auth/register`,
    checkEmail: (email) => `${SVC.auth}/auth/check-email?email=${encodeURIComponent(email)}`,
    refresh: `${SVC.auth}/auth/refresh`,
    logout: `${SVC.auth}/auth/logout`,
    me: `${SVC.auth}/auth/me`,
  },

  tasks: {
    list: `${SVC.tasks}/tasks`,
    create: `${SVC.tasks}/tasks`,
    detail: (id) => `${SVC.tasks}/tasks/${id}`,
    update: (id) => `${SVC.tasks}/tasks/${id}`,
    remove: (id, scope = 'INSTANCE') => `${SVC.tasks}/tasks/${id}?scope=${scope}`,
    complete: (id) => `${SVC.tasks}/tasks/${id}/complete`,
    reopen: (id) => `${SVC.tasks}/tasks/${id}/reopen`,

    subtasks: {
      list: (taskId) => `${SVC.tasks}/tasks/${taskId}/subtasks`,
      create: (taskId) => `${SVC.tasks}/tasks/${taskId}/subtasks`,
      toggle: (taskId, subId) => `${SVC.tasks}/tasks/${taskId}/subtasks/${subId}/toggle`,
      remove: (taskId, subId) => `${SVC.tasks}/tasks/${taskId}/subtasks/${subId}`,
    },

    note: (taskId) => `${SVC.tasks}/tasks/${taskId}/note`,
    cycleConfig: (taskId) => `${SVC.tasks}/tasks/${taskId}/cycle-config`,
    moduleConfig: (taskId) => `${SVC.tasks}/tasks/${taskId}/module-config`,
    timer: (taskId) => `${SVC.tasks}/tasks/${taskId}/timer`,
    timerLog: (taskId) => `${SVC.tasks}/tasks/${taskId}/timer/log`,
    timerStart: (taskId) => `${SVC.tasks}/tasks/${taskId}/timer/start`,
    timerStop: (taskId) => `${SVC.tasks}/tasks/${taskId}/timer/stop`,
    activeTimer: `${SVC.tasks}/timers/active`,
    focusSessions: (taskId) => `${SVC.tasks}/tasks/${taskId}/focus-sessions`,
    focusSessionComplete: (taskId, sessionId) => `${SVC.tasks}/tasks/${taskId}/focus-sessions/${sessionId}/complete`,

    // Agregado do período (tarefas, concluídas e tempo executado por dia) numa
    // requisição só. Período máximo de 92 dias; o dashboard pede uma semana.
    report: (de, ate) => `${SVC.tasks}/tasks/report?from=${de}&to=${ate}`,
  },

  // Anotações livres do usuário (não ligadas a tarefas) — task-service.
  // Só 1 nota fixada por usuário (regra server-side); GET devolve a fixada primeiro.
  notes: {
    list: `${SVC.tasks}/notes`,
    create: `${SVC.tasks}/notes`,
    update: (id) => `${SVC.tasks}/notes/${id}`,
    remove: (id) => `${SVC.tasks}/notes/${id}`,
    pin: (id) => `${SVC.tasks}/notes/${id}/pin`,
  },

  // Portabilidade: baixa TODAS as tarefas do usuário logado (com categoria,
  // cronômetro e nota) em CSV ou JSON. Não há parâmetro de usuário — o backend
  // sempre exporta o dono do token.
  notifications: {
    list: `${SVC.notif}/notifications`,
    unread: `${SVC.notif}/notifications/unread`,
    markRead: (id) => `${SVC.notif}/notifications/${id}/read`,
    remove: (id) => `${SVC.notif}/notifications/${id}`,
  },

  dados: {
    exportar: (formato) => `${SVC.tasks}/me/export?format=${encodeURIComponent(formato)}`,
    solicitarExportacao: (formato) => `${SVC.tasks}/me/exports?format=${encodeURIComponent(formato)}`,
    statusExportacao: (jobId) => `${SVC.tasks}/me/exports/${jobId}`,
  },

  categories: {
    list: `${SVC.tasks}/categories`,
    create: `${SVC.tasks}/categories`,
    update: (id) => `${SVC.tasks}/categories/${id}`,
    remove: (id) => `${SVC.tasks}/categories/${id}`,
  },

  timeBlocks: {
    list: (date) => `${SVC.sched}/time-blocks?date=${date}`,
    range: (from, to) => `${SVC.sched}/time-blocks?from=${from}&to=${to}`,
    create: `${SVC.sched}/time-blocks`,
    update: (id) => `${SVC.sched}/time-blocks/${id}`,
    remove: (id) => `${SVC.sched}/time-blocks/${id}`,
  },

  // Plano semanal (schedule-service). `atual` acha o plano pela data da segunda,
  // que é o que o frontend conhece — o id só existiria na resposta do POST e se
  // perderia no primeiro reload. O POST é idempotente (200 se a semana já existe).
  // O GET do resumo LÊ o que está salvo; quem calcula é o POST.
  weeklyPlans: {
    atual: (weekStart) => `${SVC.sched}/weekly-plans?weekStart=${weekStart}`,
    create: `${SVC.sched}/weekly-plans`,
    close: (id) => `${SVC.sched}/weekly-plans/${id}/close`,
    summary: (id) => `${SVC.sched}/weekly-plans/${id}/summary`,
  },

  analytics: {
    overall: (from, to) => `${SVC.sched}/analytics/overall?from=${from}&to=${to}`,
    week: (weekStart) => `${SVC.sched}/analytics/weeks/${weekStart}`,
  },

  weeklyClosure: {
    preview: `${SVC.tasks}/weekly-cycles/current/closure-preview`,
    close: `${SVC.tasks}/weekly-cycles/current/closure`,
    history: `${SVC.tasks}/weekly-cycles?status=CLOSED`,
    snapshots: (cycleId) => `${SVC.tasks}/weekly-cycles/${cycleId}/snapshots`,
  },

  support: {
    messages: `${SVC.notif}/support/messages`,
  },
};
