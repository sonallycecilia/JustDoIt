// ─────────────────────────────────────────────────────────────
// URLs base de cada microsserviço
// ─────────────────────────────────────────────────────────────
const BASE = {
  auth:         'http://localhost:8080',
  task:         'http://localhost:8081',
  schedule:     'http://localhost:8082',
  notification: 'http://localhost:8083',
}

// ─────────────────────────────────────────────────────────────
// HTTP helper — injeta o token em toda requisição autenticada
// ─────────────────────────────────────────────────────────────
async function http(url, options = {}) {
  const token = localStorage.getItem('token')

  const response = await fetch(url, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
  })

  if (response.status === 401 || response.status === 403) {
    localStorage.removeItem('token')
    window.location.href = '/login'
    return
  }

  if (!response.ok) {
    const error = await response.json().catch(() => ({ message: 'Erro desconhecido' }))
    throw new Error(error.message || `HTTP ${response.status}`)
  }

  if (response.status === 204) return null
  return response.json()
}

// ─────────────────────────────────────────────────────────────
// AUTH SERVICE — localhost:8080
// ─────────────────────────────────────────────────────────────
export const authApi = {
  /**
   * @param {{ name: string, email: string, password: string, birthDate: string }} data
   * birthDate formato: "yyyy-MM-dd"
   */
  register: (data) =>
    http(`${BASE.auth}/auth/register`, { method: 'POST', body: JSON.stringify(data) }),

  /**
   * @param {{ email: string, password: string }} data
   * @returns {{ token: string }}
   */
  login: (data) =>
    http(`${BASE.auth}/auth/login`, { method: 'POST', body: JSON.stringify(data) }),
}

// ─────────────────────────────────────────────────────────────
// TASK SERVICE — localhost:8081
// ─────────────────────────────────────────────────────────────
export const taskApi = {
  // ── Tarefas ──────────────────────────────────────────────

  /** Lista todas as tarefas do usuário logado */
  getAll: () =>
    http(`${BASE.task}/tasks`),

  /** @param {string} id */
  getById: (id) =>
    http(`${BASE.task}/tasks/${id}`),

  /**
   * @param {{ title: string, description?: string, categoryId?: string, priority?: string }} data
   * priority: "URGENT_IMPORTANT" | "NOT_URGENT_IMPORTANT" | "URGENT_NOT_IMPORTANT" | "NORMAL"
   */
  create: (data) =>
    http(`${BASE.task}/tasks`, { method: 'POST', body: JSON.stringify(data) }),

  /** @param {string} id @param {{ title: string, description?: string, categoryId?: string, priority?: string }} data */
  update: (id, data) =>
    http(`${BASE.task}/tasks/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  /** @param {string} id */
  delete: (id) =>
    http(`${BASE.task}/tasks/${id}`, { method: 'DELETE' }),

  /** Marca a tarefa como COMPLETED */
  complete: (id) =>
    http(`${BASE.task}/tasks/${id}/complete`, { method: 'PATCH' }),

  // ── Subtarefas ───────────────────────────────────────────

  /**
   * @param {string} taskId
   * @param {{ title: string, position?: number }} data
   */
  addSubTask: (taskId, data) =>
    http(`${BASE.task}/tasks/${taskId}/subtasks`, { method: 'POST', body: JSON.stringify(data) }),

  /**
   * Retorna um número de 0 a 1 (ex: 0.75 = 75% concluído)
   * @param {string} taskId
   */
  getSubTaskProgress: (taskId) =>
    http(`${BASE.task}/tasks/${taskId}/subtasks/progress`),

  // ── Categorias ───────────────────────────────────────────

  getAllCategories: () =>
    http(`${BASE.task}/categories`),

  getCategoryById: (id) =>
    http(`${BASE.task}/categories/${id}`),

  /**
   * @param {{ name: string, color: string, description?: string }} data
   * color: string hex, ex: "#6366f1"
   */
  createCategory: (data) =>
    http(`${BASE.task}/categories`, { method: 'POST', body: JSON.stringify(data) }),

  updateCategory: (id, data) =>
    http(`${BASE.task}/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) }),

  deleteCategory: (id) =>
    http(`${BASE.task}/categories/${id}`, { method: 'DELETE' }),
}

// ─────────────────────────────────────────────────────────────
// SCHEDULE SERVICE — localhost:8082
// ─────────────────────────────────────────────────────────────
export const scheduleApi = {
  // ── Blocos de tempo ──────────────────────────────────────

  /**
   * @param {string} date formato: "yyyy-MM-dd"
   */
  getTimeBlocksByDate: (date) =>
    http(`${BASE.schedule}/time-blocks?date=${date}`),

  /**
   * @param {{
   *   taskId?: string,
   *   startDateTime: string,
   *   endDateTime: string,
   *   estimatedMinutes?: number,
   *   date: string
   * }} data
   * startDateTime / endDateTime formato: "yyyy-MM-ddTHH:mm:ss"
   */
  createTimeBlock: (data) =>
    http(`${BASE.schedule}/time-blocks`, { method: 'POST', body: JSON.stringify(data) }),

  // ── Plano semanal ─────────────────────────────────────────

  /**
   * @param {{ weekStartDate: string, weekEndDate: string }} data
   * formato: "yyyy-MM-dd"
   */
  createWeeklyPlan: (data) =>
    http(`${BASE.schedule}/weekly-plans`, { method: 'POST', body: JSON.stringify(data) }),

  /** Fecha o plano (status: OPEN → CLOSED) */
  closeWeeklyPlan: (id) =>
    http(`${BASE.schedule}/weekly-plans/${id}/close`, { method: 'PATCH' }),

  /** Gera e retorna o resumo da semana */
  getWeeklySummary: (weeklyPlanId) =>
    http(`${BASE.schedule}/weekly-plans/${weeklyPlanId}/summary`),
}

// ─────────────────────────────────────────────────────────────
// NOTIFICATION SERVICE — localhost:8083
// ─────────────────────────────────────────────────────────────
export const notificationApi = {
  /** Lista todas as notificações do usuário */
  getAll: () =>
    http(`${BASE.notification}/notifications`),

  /** Lista apenas as não lidas */
  getUnread: () =>
    http(`${BASE.notification}/notifications/unread`),

  /** @param {string} id */
  markAsRead: (id) =>
    http(`${BASE.notification}/notifications/${id}/read`, { method: 'PATCH' }),

  /** Busca as preferências (cria automaticamente se não existir) */
  getPreferences: () =>
    http(`${BASE.notification}/notifications/preferences`),

  /**
   * @param {{
   *   notifyOnComplete?: boolean,
   *   notifyOnOverdue?: boolean,
   *   notifyOnCycleReset?: boolean
   * }} data
   */
  updatePreferences: (data) =>
    http(`${BASE.notification}/notifications/preferences`, {
      method: 'PUT',
      body: JSON.stringify(data),
    }),
}
