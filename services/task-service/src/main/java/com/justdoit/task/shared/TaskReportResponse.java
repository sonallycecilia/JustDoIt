package com.justdoit.task.shared;

import java.time.LocalDate;
import java.util.List;

/**
 * Agregado do período consumido pelo schedule-service (resumo semanal e
 * /analytics/weekly). Semântica:
 * - totalTasks: tarefas com dueDate dentro do período;
 * - completedTasks: tarefas concluídas (completedAt) dentro do período,
 *   independentemente do dueDate;
 * - actualSeconds: tempo executado no período, somando as DUAS formas de
 *   registrar trabalho — sessões de foco (Pomodoro) e intervalos do cronômetro
 *   (TimeEntry). O dia é o do INÍCIO em ambos os casos. Sessões de PAUSA não
 *   contam: pausa não é tempo trabalhado. focusSeconds e timerSeconds dão a
 *   quebra, para a tela poder dizer de onde veio o tempo;
 * - estimatedMinutes: soma das estimativas das tarefas, atribuída ao dueDate.
 *   É o "estimado" do gráfico do dashboard, distinto do "agendado" (que sai dos
 *   blocos do calendário, no schedule-service): uma tarefa pode ter estimativa
 *   sem nunca ter entrado na agenda;
 * - byDay: um item por dia do período, inclusive dias zerados. focusSessions é
 *   a contagem de ciclos que renderam tempo naquele dia — é o "N ciclos de
 *   Pomodoro" do dashboard, que não dá para deduzir dos segundos.
 */
public record TaskReportResponse(
        LocalDate from,
        LocalDate to,
        long totalTasks,
        long completedTasks,
        long totalActualSeconds,
        long totalEstimatedMinutes,
        List<DaySummary> byDay,
        long dueTasksCompleted,
        long completedInPeriod,
        long overdueOpenTasks,
        long undatedOpenTasks,
        long undatedCompletedTasks,
        long undatedEstimatedMinutes,
        long totalMeasuredSeconds,
        long totalInferredSeconds,
        List<CategorySummary> byCategory,
        List<TaskPerformance> taskPerformance
) {
    /** Construtor compatível com consumidores e testes do contrato anterior. */
    public TaskReportResponse(LocalDate from, LocalDate to, long totalTasks, long completedTasks,
                              long totalActualSeconds, long totalEstimatedMinutes, List<DaySummary> byDay) {
        this(from, to, totalTasks, completedTasks, totalActualSeconds, totalEstimatedMinutes, byDay,
                0, completedTasks, 0, 0, 0, 0, totalActualSeconds, 0, List.of(), List.of());
    }

    public record DaySummary(LocalDate date,
                             long actualSeconds,
                             long focusSeconds,
                             long timerSeconds,
                             long completedTasks,
                             long focusSessions,
                             long estimatedMinutes,
                             long measuredSeconds,
                             long inferredSeconds) {
        public DaySummary(LocalDate date, long actualSeconds, long focusSeconds, long timerSeconds,
                          long completedTasks, long focusSessions, long estimatedMinutes) {
            this(date, actualSeconds, focusSeconds, timerSeconds, completedTasks, focusSessions,
                    estimatedMinutes, actualSeconds, 0);
        }
    }

    public record CategorySummary(java.util.UUID categoryId,
                                  String categoryName,
                                  String categoryColor,
                                  long estimatedMinutes,
                                  long measuredSeconds,
                                  long inferredSeconds,
                                  long dueTasks,
                                  long dueTasksCompleted) { }

    public record TaskPerformance(java.util.UUID taskId,
                                  String title,
                                  long estimatedMinutes,
                                  long actualSeconds,
                                  long deviationSeconds) { }
}
