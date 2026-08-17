package com.justdoit.task.feature.report;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.task.TaskEstimates;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.timer.TimeEntry;
import com.justdoit.task.feature.timer.TimeEntryRepository;

import com.justdoit.task.shared.SessionType;
import com.justdoit.task.shared.TaskReportResponse;
import com.justdoit.task.shared.TaskStatus;
import com.justdoit.task.shared.TimeEntrySource;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Comparator;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskReportService {

    // Teto de segurança: o consumidor real (schedule-service) pede semanas; um
    // range gigante viraria varredura cara da base inteira do usuário.
    static final int MAX_RANGE_DAYS = 92;

    private final TaskRepository taskRepository;
    private final FocusSessionRepository focusSessionRepository;
    private final TimeEntryRepository timeEntryRepository;

    public TaskReportResponse getReport(UUID userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Período inválido: informe from <= to");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Período máximo de " + MAX_RANGE_DAYS + " dias");
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        // [0]=focusSeconds, [1]=completedTasks, [2]=focusSessions,
        // [3]=estimatedMinutes, [4]=timerSeconds, [5]=measuredSeconds,
        // [6]=inferredSeconds
        Map<LocalDate, long[]> byDay = new HashMap<>();
        // categoria: [estimadoMin, medidoSeg, inferidoSeg, previstas, previstasConcluidas]
        Map<UUID, long[]> byCategory = new HashMap<>();
        Map<UUID, String> categoryNames = new HashMap<>();
        Map<UUID, String> categoryColors = new HashMap<>();
        Map<UUID, Task> reportTasks = new HashMap<>();
        Map<UUID, Long> actualByTask = new HashMap<>();

        // Tarefas que VENCEM no período: dão o total e a estimativa de cada dia.
        List<Task> due = taskRepository.findByUserIdAndDueDateBetweenWithTimer(userId, from, to);
        for (Task task : due) {
            byDay.computeIfAbsent(task.getDueDate(), d -> new long[7])[3] += TaskEstimates.minutesOrZero(task);
            addEstimatedCategory(byCategory, categoryNames, categoryColors, task, true);
            reportTasks.put(task.getId(), task);
        }
        long totalTasks = due.size();
        long dueTasksCompleted = due.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .count();

        List<Task> undated = taskRepository.findUndatedByUserIdWithTimer(userId);
        undated.forEach(task -> {
            addEstimatedCategory(byCategory, categoryNames, categoryColors, task, false);
            reportTasks.put(task.getId(), task);
        });
        long undatedCompletedTasks = undated.stream()
                .filter(task -> task.getStatus() == TaskStatus.COMPLETED)
                .count();
        long undatedOpenTasks = undated.size() - undatedCompletedTasks;
        long undatedEstimatedMinutes = undated.stream()
                .mapToLong(TaskEstimates::minutesOrZero)
                .sum();
        long overdueOpenTasks = taskRepository.countOverdueOpen(userId, from);

        List<Task> completed = taskRepository.findByUserIdAndCompletedAtBetween(userId, start, end);
        for (Task task : completed) {
            byDay.computeIfAbsent(task.getCompletedAt().toLocalDate(), d -> new long[7])[1]++;
        }

        List<FocusSession> sessions = focusSessionRepository.findByTask_UserIdAndStartedAtBetween(userId, start, end);
        for (FocusSession session : sessions) {
            if (session.getSessionType() == SessionType.BREAK) continue; // pausa não é tempo trabalhado
            long seconds = sessionSeconds(session);
            if (seconds > 0) {
                long[] agg = byDay.computeIfAbsent(session.getStartedAt().toLocalDate(), d -> new long[7]);
                agg[0] += seconds;
                agg[2]++;
                agg[5] += seconds;
                addActual(byCategory, categoryNames, categoryColors, actualByTask, reportTasks,
                        session.getTask(), seconds, false);
            }
        }

        // Intervalos do cronômetro. Foco e cronômetro são formas independentes de
        // registrar trabalho, então somam; quem usa as duas ao mesmo tempo na
        // mesma hora conta o tempo duas vezes, e isso é o que ele de fato pediu
        // ao ligar os dois.
        List<TimeEntry> entries = timeEntryRepository.findByTask_UserIdAndStartedAtBetween(userId, start, end);
        for (TimeEntry entry : entries) {
            if (entry.getSeconds() > 0) {
                long[] agg = byDay.computeIfAbsent(entry.getStartedAt().toLocalDate(), d -> new long[7]);
                agg[4] += entry.getSeconds();
                if (entry.getSource() == TimeEntrySource.COMPLETION_ESTIMATE) {
                    agg[6] += entry.getSeconds();
                    addActual(byCategory, categoryNames, categoryColors, actualByTask, reportTasks,
                            entry.getTask(), entry.getSeconds(), true);
                } else {
                    agg[5] += entry.getSeconds();
                    addActual(byCategory, categoryNames, categoryColors, actualByTask, reportTasks,
                            entry.getTask(), entry.getSeconds(), false);
                }
            }
        }

        List<TaskReportResponse.DaySummary> days = new ArrayList<>();
        long totalActualSeconds = 0;
        long totalEstimatedMinutes = 0;
        long totalMeasuredSeconds = 0;
        long totalInferredSeconds = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            long[] agg = byDay.getOrDefault(date, new long[7]);
            long trabalhado = agg[0] + agg[4];
            totalActualSeconds += trabalhado;
            totalEstimatedMinutes += agg[3];
            totalMeasuredSeconds += agg[5];
            totalInferredSeconds += agg[6];
            days.add(new TaskReportResponse.DaySummary(date, trabalhado, agg[0], agg[4],
                    agg[1], agg[2], agg[3], agg[5], agg[6]));
        }

        List<TaskReportResponse.CategorySummary> categories = byCategory.entrySet().stream()
                .map(entry -> new TaskReportResponse.CategorySummary(entry.getKey(),
                        categoryNames.get(entry.getKey()), categoryColors.get(entry.getKey()),
                        entry.getValue()[0], entry.getValue()[1],
                        entry.getValue()[2], entry.getValue()[3], entry.getValue()[4]))
                .sorted(Comparator.comparingLong((TaskReportResponse.CategorySummary c) ->
                        c.measuredSeconds() + c.inferredSeconds()).reversed())
                .toList();

        List<TaskReportResponse.TaskPerformance> taskPerformance = reportTasks.values().stream()
                .map(task -> {
                    long estimated = TaskEstimates.minutesOrZero(task);
                    long actual = actualByTask.getOrDefault(task.getId(), 0L);
                    return new TaskReportResponse.TaskPerformance(task.getId(), task.getTitle(), estimated,
                            actual, actual - estimated * 60L);
                })
                .filter(item -> item.estimatedMinutes() > 0 || item.actualSeconds() > 0)
                .sorted(Comparator.comparingLong((TaskReportResponse.TaskPerformance item) ->
                        Math.abs(item.deviationSeconds())).reversed())
                .limit(10)
                .toList();

        return new TaskReportResponse(from, to, totalTasks, completed.size(),
                totalActualSeconds, totalEstimatedMinutes, days,
                dueTasksCompleted, completed.size(), overdueOpenTasks,
                undatedOpenTasks, undatedCompletedTasks, undatedEstimatedMinutes,
                totalMeasuredSeconds, totalInferredSeconds, categories, taskPerformance);
    }

    private static void addEstimatedCategory(Map<UUID, long[]> categories, Map<UUID, String> names,
                                             Map<UUID, String> colors, Task task, boolean dueInPeriod) {
        UUID categoryId = task.getCategory() != null ? task.getCategory().getId() : null;
        names.put(categoryId, task.getCategory() != null ? task.getCategory().getName() : "Sem categoria");
        colors.put(categoryId, task.getCategory() != null ? task.getCategory().getColor() : "#94a3b8");
        long[] aggregate = categories.computeIfAbsent(categoryId, ignored -> new long[5]);
        aggregate[0] += TaskEstimates.minutesOrZero(task);
        if (dueInPeriod) {
            aggregate[3]++;
            if (task.getStatus() == TaskStatus.COMPLETED) aggregate[4]++;
        }
    }

    private static void addActual(Map<UUID, long[]> categories, Map<UUID, String> names,
                                  Map<UUID, String> colors,
                                  Map<UUID, Long> actualByTask, Map<UUID, Task> reportTasks,
                                  Task task, long seconds, boolean inferred) {
        if (task == null) return; // tolera registros legados e fixtures antigas
        UUID categoryId = task.getCategory() != null ? task.getCategory().getId() : null;
        names.put(categoryId, task.getCategory() != null ? task.getCategory().getName() : "Sem categoria");
        colors.put(categoryId, task.getCategory() != null ? task.getCategory().getColor() : "#94a3b8");
        long[] aggregate = categories.computeIfAbsent(categoryId, ignored -> new long[5]);
        aggregate[inferred ? 2 : 1] += seconds;
        actualByTask.merge(task.getId(), seconds, Long::sum);
        reportTasks.put(task.getId(), task);
    }

    /**
     * Duração de uma sessão de foco: intervalo started→ended quando os dois
     * existem; senão, os minutos planejados de uma sessão marcada como concluída;
     * sessões abertas/abandonadas não contam.
     */
    private static long sessionSeconds(FocusSession session) {
        if (session.getStartedAt() != null && session.getEndedAt() != null
                && session.getEndedAt().isAfter(session.getStartedAt())) {
            return Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
        }
        if (Boolean.TRUE.equals(session.getCompleted()) && session.getFocusMinutes() != null) {
            return session.getFocusMinutes() * 60L;
        }
        return 0;
    }
}
