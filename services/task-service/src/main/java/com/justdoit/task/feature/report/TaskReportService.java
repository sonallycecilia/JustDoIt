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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
        // [3]=estimatedMinutes, [4]=timerSeconds
        Map<LocalDate, long[]> byDay = new HashMap<>();

        // Tarefas que VENCEM no período: dão o total e a estimativa de cada dia.
        List<Task> due = taskRepository.findByUserIdAndDueDateBetweenWithTimer(userId, from, to);
        for (Task task : due) {
            byDay.computeIfAbsent(task.getDueDate(), d -> new long[5])[3] += TaskEstimates.minutesOrZero(task);
        }
        long totalTasks = due.size();

        List<Task> completed = taskRepository.findByUserIdAndCompletedAtBetween(userId, start, end);
        for (Task task : completed) {
            byDay.computeIfAbsent(task.getCompletedAt().toLocalDate(), d -> new long[5])[1]++;
        }

        List<FocusSession> sessions = focusSessionRepository.findByTask_UserIdAndStartedAtBetween(userId, start, end);
        for (FocusSession session : sessions) {
            if (session.getSessionType() == SessionType.BREAK) continue; // pausa não é tempo trabalhado
            long seconds = sessionSeconds(session);
            if (seconds > 0) {
                long[] agg = byDay.computeIfAbsent(session.getStartedAt().toLocalDate(), d -> new long[5]);
                agg[0] += seconds;
                agg[2]++;
            }
        }

        // Intervalos do cronômetro. Foco e cronômetro são formas independentes de
        // registrar trabalho, então somam; quem usa as duas ao mesmo tempo na
        // mesma hora conta o tempo duas vezes, e isso é o que ele de fato pediu
        // ao ligar os dois.
        List<TimeEntry> entries = timeEntryRepository.findByTask_UserIdAndStartedAtBetween(userId, start, end);
        for (TimeEntry entry : entries) {
            if (entry.getSeconds() > 0) {
                byDay.computeIfAbsent(entry.getStartedAt().toLocalDate(), d -> new long[5])[4] += entry.getSeconds();
            }
        }

        List<TaskReportResponse.DaySummary> days = new ArrayList<>();
        long totalActualSeconds = 0;
        long totalEstimatedMinutes = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            long[] agg = byDay.getOrDefault(date, new long[5]);
            long trabalhado = agg[0] + agg[4];
            totalActualSeconds += trabalhado;
            totalEstimatedMinutes += agg[3];
            days.add(new TaskReportResponse.DaySummary(date, trabalhado, agg[0], agg[4], agg[1], agg[2], agg[3]));
        }

        return new TaskReportResponse(from, to, totalTasks, completed.size(),
                totalActualSeconds, totalEstimatedMinutes, days);
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
