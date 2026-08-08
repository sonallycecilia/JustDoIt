package com.justdoit.task.feature.report;
import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.feature.timer.TimeEntry;
import com.justdoit.task.feature.timer.TimeEntryRepository;

import com.justdoit.task.shared.SessionType;
import com.justdoit.task.shared.TaskReportResponse;
import com.justdoit.task.shared.TaskStatus;
import com.justdoit.task.shared.TimeEntrySource;
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
    @Mock private TimeEntryRepository timeEntryRepository;
    @InjectMocks private TaskReportService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate SEG = LocalDate.of(2026, 6, 29); // segunda
    private static final LocalDate DOM = SEG.plusDays(6);           // domingo

    private Task tarefaConcluidaEm(LocalDateTime quando) {
        return Task.builder().id(UUID.randomUUID()).userId(USER_ID)
                .title("t").completedAt(quando).build();
    }

    /** Tarefa que vence num dia, com a estimativa onde ela mora de verdade (o timer). */
    private Task tarefaComVencimento(LocalDate dia, Integer estimadoMin) {
        Task task = Task.builder().id(UUID.randomUUID()).userId(USER_ID)
                .title("t").dueDate(dia).build();
        if (estimadoMin != null) {
            task.setTimer(TaskTimer.builder().task(task).estimatedMinutes(estimadoMin).build());
        }
        return task;
    }

    private FocusSession sessao(LocalDateTime inicio, LocalDateTime fim, Integer focusMin, boolean completed) {
        return FocusSession.builder().id(UUID.randomUUID())
                .startedAt(inicio).endedAt(fim).focusMinutes(focusMin).completed(completed).build();
    }

    private TimeEntry intervalo(LocalDateTime inicio, long segundos) {
        return TimeEntry.builder().id(UUID.randomUUID())
                .startedAt(inicio).endedAt(inicio.plusSeconds(segundos)).seconds(segundos).build();
    }

    private FocusSession pausa(LocalDateTime inicio, LocalDateTime fim) {
        return FocusSession.builder().id(UUID.randomUUID()).sessionType(SessionType.BREAK)
                .startedAt(inicio).endedAt(fim).completed(true).build();
    }

    @Test
    @DisplayName("agrega concluídas e tempo executado por dia, preenchendo dias zerados")
    void report_agregaPorDia_comDiasZerados() {
        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, DOM)).thenReturn(List.of(
                tarefaComVencimento(SEG, 60),
                tarefaComVencimento(SEG, 30),
                tarefaComVencimento(SEG.plusDays(2), 45),
                tarefaComVencimento(SEG.plusDays(2), null), // sem estimativa: entra no total, não na soma
                tarefaComVencimento(SEG.plusDays(4), 120)
        ));
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
        assertEquals(60 + 30 + 45 + 120, report.totalEstimatedMinutes());
        assertEquals(7, report.byDay().size()); // todos os dias da semana presentes

        TaskReportResponse.DaySummary segunda = report.byDay().get(0);
        assertEquals(SEG, segunda.date());
        assertEquals(90 * 60, segunda.actualSeconds());
        assertEquals(90 * 60, segunda.focusSeconds());
        assertEquals(0, segunda.timerSeconds()); // nada de cronômetro neste caso
        assertEquals(2, segunda.completedTasks());
        assertEquals(1, segunda.focusSessions());
        assertEquals(90, segunda.estimatedMinutes());

        TaskReportResponse.DaySummary quarta = report.byDay().get(2);
        assertEquals(25 * 60, quarta.actualSeconds());
        assertEquals(1, quarta.completedTasks());
        assertEquals(1, quarta.focusSessions());
        assertEquals(45, quarta.estimatedMinutes()); // a tarefa sem estimativa não soma

        TaskReportResponse.DaySummary quinta = report.byDay().get(3);
        assertEquals(0, quinta.focusSessions()); // a sessão abandonada não vira ciclo

        // Sexta: tem estimativa e nenhum tempo executado — o "planejado sem execução"
        TaskReportResponse.DaySummary sexta = report.byDay().get(4);
        assertEquals(120, sexta.estimatedMinutes());
        assertEquals(0, sexta.actualSeconds());

        TaskReportResponse.DaySummary domingo = report.byDay().get(6);
        assertEquals(0, domingo.actualSeconds());
        assertEquals(0, domingo.completedTasks());
        assertEquals(0, domingo.focusSessions());
        assertEquals(0, domingo.estimatedMinutes());
    }

    @Test
    @DisplayName("sessão de PAUSA não conta como tempo trabalhado nem como ciclo")
    void report_ignoraPausa() {
        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, SEG)).thenReturn(List.of());
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of(
                sessao(SEG.atTime(9, 0), SEG.atTime(9, 25), 25, true),   // sessionType null = legado, conta como foco
                pausa(SEG.atTime(9, 25), SEG.atTime(9, 30))
        ));

        TaskReportResponse report = service.getReport(USER_ID, SEG, SEG);

        assertEquals(25 * 60, report.totalActualSeconds());
        assertEquals(1, report.byDay().get(0).focusSessions());
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
        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, SEG)).thenReturn(List.of());
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());

        TaskReportResponse report = service.getReport(USER_ID, SEG, SEG);

        assertEquals(1, report.byDay().size());
        assertEquals(0, report.totalActualSeconds());
    }

    @Test
    @DisplayName("tempo do cronômetro entra no executado, somado ao foco e separável dele")
    void report_somaTempoDoCronometro() {
        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, DOM)).thenReturn(List.of());
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of(
                sessao(SEG.atTime(9, 0), SEG.atTime(9, 25), 25, true) // 25 min de foco na segunda
        ));
        when(timeEntryRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of(
                intervalo(SEG.atTime(14, 0), 30 * 60),          // 30 min de cronômetro na segunda
                intervalo(SEG.plusDays(1).atTime(8, 0), 45 * 60) // 45 min na terça
        ));

        TaskReportResponse report = service.getReport(USER_ID, SEG, DOM);

        TaskReportResponse.DaySummary segunda = report.byDay().get(0);
        assertEquals(25 * 60, segunda.focusSeconds());
        assertEquals(30 * 60, segunda.timerSeconds());
        assertEquals(55 * 60, segunda.actualSeconds()); // o executado do dia é a soma

        TaskReportResponse.DaySummary terca = report.byDay().get(1);
        assertEquals(0, terca.focusSeconds());
        assertEquals(45 * 60, terca.timerSeconds());
        assertEquals(45 * 60, terca.actualSeconds());

        assertEquals((25 + 30 + 45) * 60, report.totalActualSeconds());
    }

    @Test
    @DisplayName("intervalo do cronômetro cai no dia em que COMEÇOU, mesmo virando a madrugada")
    void report_intervaloAtravessandoMeiaNoite() {
        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, DOM)).thenReturn(List.of());
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(timeEntryRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of(
                intervalo(SEG.atTime(23, 40), 40 * 60) // começa 23h40 da segunda, termina 00h20 da terça
        ));

        TaskReportResponse report = service.getReport(USER_ID, SEG, DOM);

        assertEquals(40 * 60, report.byDay().get(0).timerSeconds());
        assertEquals(0, report.byDay().get(1).timerSeconds());
    }

    @Test
    @DisplayName("estimativa sai do cronômetro; a coluna legada da tarefa é só fallback")
    void report_estimativaVemDoTimer() {
        Task legada = Task.builder().id(UUID.randomUUID()).userId(USER_ID)
                .title("t").dueDate(SEG).estimatedMinutes(15).build(); // sem timer
        Task comTimer = tarefaComVencimento(SEG, 40);
        comTimer.setEstimatedMinutes(999); // a coluna legada perde para o timer

        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, SEG))
                .thenReturn(List.of(legada, comTimer));
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());

        TaskReportResponse report = service.getReport(USER_ID, SEG, SEG);

        assertEquals(15 + 40, report.totalEstimatedMinutes());
    }

    @Test
    @DisplayName("separa tarefas sem data e tempo medido do inferido na conclusão")
    void report_separaSemDataETempoInferido() {
        Task pendente = tarefaComVencimento(null, 300);
        Task concluida = tarefaComVencimento(null, null);
        concluida.setStatus(TaskStatus.COMPLETED);

        TimeEntry medido = intervalo(SEG.atTime(9, 0), 1800);
        medido.setSource(TimeEntrySource.TIMER);
        TimeEntry inferido = intervalo(SEG.atTime(10, 0), 3600);
        inferido.setSource(TimeEntrySource.COMPLETION_ESTIMATE);

        when(taskRepository.findByUserIdAndDueDateBetweenWithTimer(USER_ID, SEG, DOM)).thenReturn(List.of());
        when(taskRepository.findUndatedByUserIdWithTimer(USER_ID)).thenReturn(List.of(pendente, concluida));
        when(taskRepository.countOverdueOpen(USER_ID, SEG)).thenReturn(2L);
        when(taskRepository.findByUserIdAndCompletedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(focusSessionRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any())).thenReturn(List.of());
        when(timeEntryRepository.findByTask_UserIdAndStartedAtBetween(eq(USER_ID), any(), any()))
                .thenReturn(List.of(medido, inferido));

        TaskReportResponse report = service.getReport(USER_ID, SEG, DOM);

        assertEquals(1, report.undatedOpenTasks());
        assertEquals(1, report.undatedCompletedTasks());
        assertEquals(300, report.undatedEstimatedMinutes());
        assertEquals(2, report.overdueOpenTasks());
        assertEquals(1800, report.totalMeasuredSeconds());
        assertEquals(3600, report.totalInferredSeconds());
    }
}
