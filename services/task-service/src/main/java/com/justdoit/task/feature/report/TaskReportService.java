package com.justdoit.task.feature.report;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

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

    public TaskReportResponse getReport(UUID userId, LocalDate from, LocalDate to) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Período inválido: informe from <= to");
        }
        if (java.time.temporal.ChronoUnit.DAYS.between(from, to) + 1 > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Período máximo de " + MAX_RANGE_DAYS + " dias");
        }

        LocalDateTime start = from.atStartOfDay();
        LocalDateTime end = to.plusDays(1).atStartOfDay();

        long totalTasks = taskRepository.countByUserIdAndDueDateBetween(userId, from, to);

        Map<LocalDate, long[]> byDay = new HashMap<>(); // [0]=actualSeconds, [1]=completedTasks

        List<Task> completed = taskRepository.findByUserIdAndCompletedAtBetween(userId, start, end);
        for (Task task : completed) {
            byDay.computeIfAbsent(task.getCompletedAt().toLocalDate(), d -> new long[2])[1]++;
        }

        List<FocusSession> sessions = focusSessionRepository.findByTask_UserIdAndStartedAtBetween(userId, start, end);
        for (FocusSession session : sessions) {
            long seconds = sessionSeconds(session);
            if (seconds > 0) {
                byDay.computeIfAbsent(session.getStartedAt().toLocalDate(), d -> new long[2])[0] += seconds;
            }
        }

        List<TaskReportResponse.DaySummary> days = new ArrayList<>();
        long totalActualSeconds = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            long[] agg = byDay.getOrDefault(date, new long[2]);
            totalActualSeconds += agg[0];
            days.add(new TaskReportResponse.DaySummary(date, agg[0], agg[1]));
        }

        return new TaskReportResponse(from, to, totalTasks, completed.size(), totalActualSeconds, days);
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
