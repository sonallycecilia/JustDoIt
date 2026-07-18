package com.justdoit.task.shared;

import java.time.LocalDate;
import java.util.List;

/**
 * Agregado do período consumido pelo schedule-service (resumo semanal e
 * /analytics/weekly). Semântica:
 * - totalTasks: tarefas com dueDate dentro do período;
 * - completedTasks: tarefas concluídas (completedAt) dentro do período,
 *   independentemente do dueDate;
 * - actualSeconds: tempo executado vindo das FocusSessions (o TaskTimer é
 *   acumulado sem data e não entra no recorte por período);
 * - byDay: um item por dia do período, inclusive dias zerados.
 */
public record TaskReportResponse(
        LocalDate from,
        LocalDate to,
        long totalTasks,
        long completedTasks,
        long totalActualSeconds,
        List<DaySummary> byDay
) {
    public record DaySummary(LocalDate date, long actualSeconds, long completedTasks) { }
}
