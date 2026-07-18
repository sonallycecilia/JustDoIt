package com.justdoit.task.feature.report;
import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.TaskReportResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskReportServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private FocusSessionRepository focusSessionRepository;
    @InjectMocks private TaskReportService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate SEG = LocalDate.of(2026, 6, 29); // segunda
    private static final LocalDate DOM = SEG.plusDays(6);           // domingo

    private Task tarefaConcluidaEm(LocalDateTime quando) {
        return Task.builder().id(UUID.randomUUID()).userId(USER_ID)
                .title("t").completedAt(quando).build();
    }

    private FocusSession sessao(LocalDateTime inicio, LocalDateTime fim, Integer focusMin, boolean completed) {
        return FocusSession.builder().id(UUID.randomUUID())
                .startedAt(inicio).endedAt(fim).focusMinutes(focusMin).completed(completed).build();
    }

    @Test
    @DisplayName("agrega concluídas e tempo executado por dia, preenchendo dias zerados")
    void report_agregaPorDia_comDiasZerados() {
        when(taskRepository.countByUserIdAndDueDateBetween(USER_ID, SEG, DOM)).thenReturn(5L);
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of(
                tarefaConcluidaEm(SEG.atTime(10, 0)),
                tarefaConcluidaEm(SEG.atTime(18, 30)),
                tarefaConcluidaEm(SEG.plusDays(2).atTime(9, 0))
        ));
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of(
                // 1h30 por intervalo started→ended na segunda
                sessao(SEG.atTime(9, 0), SEG.atTime(10, 30), 25, true),
                // sem endedAt mas concluída: vale focusMinutes (25 min) na quarta
                sessao(SEG.plusDays(2).atTime(14, 0), null, 25, true),
                // aberta/abandonada: não conta
                sessao(SEG.plusDays(3).atTime(8, 0), null, 25, false)
        ));

        TaskReportResponse report = service.getReport(USER_ID, SEG, DOM);

        assertEquals(5, report.totalTasks());
        assertEquals(3, report.completedTasks());
        assertEquals(90 * 60 + 25 * 60, report.totalActualSeconds());
        assertEquals(7, report.byDay().size()); // todos os dias da semana presentes

        TaskReportResponse.DaySummary segunda = report.byDay().get(0);
        assertEquals(SEG, segunda.date());
        assertEquals(90 * 60, segunda.actualSeconds());
        assertEquals(2, segunda.completedTasks());

        TaskReportResponse.DaySummary quarta = report.byDay().get(2);
        assertEquals(25 * 60, quarta.actualSeconds());
        assertEquals(1, quarta.completedTasks());

        TaskReportResponse.DaySummary domingo = report.byDay().get(6);
        assertEquals(0, domingo.actualSeconds());
        assertEquals(0, domingo.completedTasks());
    }

    @Test
    @DisplayName("rejeita período invertido e período acima do teto")
    void report_rejeitaPeriodosInvalidos() {
        assertThrows(IllegalArgumentException.class, () -> service.getReport(USER_ID, DOM, SEG));
        assertThrows(IllegalArgumentException.class,
                () -> service.getReport(USER_ID, SEG, SEG.plusDays(TaskReportService.MAX_RANGE_DAYS)));
    }

    @Test
    @DisplayName("período de um dia funciona (from == to)")
    void report_periodoDeUmDia() {
        when(taskRepository.countByUserIdAndDueDateBetween(USER_ID, SEG, SEG)).thenReturn(0L);
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());

        TaskReportResponse report = service.getReport(USER_ID, SEG, SEG);

        assertEquals(1, report.byDay().size());
        assertEquals(0, report.totalActualSeconds());
    }
}
