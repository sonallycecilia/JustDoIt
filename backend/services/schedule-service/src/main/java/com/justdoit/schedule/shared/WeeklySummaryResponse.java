package com.justdoit.schedule.shared;

import java.util.UUID;

/**
 * Retrato da semana. São TRÊS grandezas diferentes, de propósito:
 * - totalScheduledMinutes: o que foi para a agenda (blocos do calendário);
 * - totalEstimatedMinutes: o que foi estimado nas tarefas (vem do task-service);
 * - totalActualSeconds: o que foi de fato executado (sessões de foco).
 *
 * deviationSeconds mede o executado contra a AGENDA (executado menos agendado),
 * que é o compromisso que o usuário assumiu ao marcar o horário no calendário.
 */
public record WeeklySummaryResponse(
    UUID id,
    UUID weeklyPlanId,
    Integer totalScheduledMinutes,
    Integer totalEstimatedMinutes,
    Long totalActualSeconds,
    Long deviationSeconds,
    Integer completedTasks,
    Integer totalTasks,
    SummaryDataStatus dataStatus
) {
    public WeeklySummaryResponse(UUID id, UUID weeklyPlanId, Integer totalScheduledMinutes,
                                 Integer totalEstimatedMinutes, Long totalActualSeconds,
                                 Long deviationSeconds, Integer completedTasks, Integer totalTasks) {
        this(id, weeklyPlanId, totalScheduledMinutes, totalEstimatedMinutes, totalActualSeconds,
                deviationSeconds, completedTasks, totalTasks, SummaryDataStatus.COMPLETE);
    }
}
