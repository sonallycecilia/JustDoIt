import { api } from '@/api/client';
import { endpoints } from '@/api/endpoints';

// IMPORTANTE: api.get/api.post (src/api/client.js) já devolvem o corpo JSON
// já parseado (não é axios) — não existe um envelope `{ data: ... }`. Ler
// `response.data` aqui sempre resultava em `undefined`.
export const closureService = {
  getClosurePreview: async () => {
    return api.get(endpoints.weeklyClosure.preview);
  },

  submitClosure: async (command) => {
    // O task-service faz a triagem/migração. Em seguida o schedule-service
    // congela o relatório e os blocos da MESMA semana. As duas operações são
    // idempotentes, então um erro de rede entre elas pode ser repetido com
    // segurança sem duplicar ciclo nem snapshot.
    await api.post(endpoints.weeklyClosure.close, {
      cycleId: command.cycleId,
      tasksToMigrate: command.tasksToMigrate,
      tasksToArchive: command.tasksToArchive,
    });

    const plano = await api.post(endpoints.weeklyPlans.create, {
      weekStartDate: command.weekStartDate,
      weekEndDate: command.weekEndDate,
    });
    return api.patch(endpoints.weeklyPlans.close(plano.id), {});
  },

  getClosedCycles: async () => {
    return api.get(endpoints.weeklyClosure.history);
  },

  getCycleSnapshots: async (cycleId) => {
    return api.get(endpoints.weeklyClosure.snapshots(cycleId));
  }
};
