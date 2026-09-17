package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.integration.TaskReportClient;
import com.justdoit.schedule.integration.TaskReportClient.TaskReport;
import com.justdoit.schedule.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {

    @Mock private TimeBlockRepository timeBlockRepository;
    @Mock private WeeklyPlanRepository weeklyPlanRepository;
    @Mock private WeeklySummaryRepository weeklySummaryRepository;
    @Mock private TaskReportClient taskReportClient;
    @InjectMocks private ScheduleService service;

    private static final String AUTH_HEADER = "Bearer mock-token";
    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BLOCK_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final LocalDate TODAY = LocalDate.of(2026, 6, 21);
    private final LocalDateTime START = LocalDateTime.of(2026, 6, 21, 9, 0);
    private final LocalDateTime END   = LocalDateTime.of(2026, 6, 21, 10, 0);

    private TimeBlock timeBlock;
    private WeeklyPlan weeklyPlan;

    @BeforeEach
    void setUp() {
        timeBlock = TimeBlock.builder()
                .id(BLOCK_ID).userId(USER_ID)
                .startDateTime(START).endDateTime(END)
                .estimatedMinutes(60).date(TODAY)
                .build();

        weeklyPlan = WeeklyPlan.builder()
                .id(PLAN_ID).userId(USER_ID)
                .weekStartDate(TODAY).weekEndDate(TODAY.plusDays(6))
                .status(ScheduleStatus.OPEN)
                .build();
    }

    @Test
    void createTimeBlock_savesAndReturnsResponse() {
        TimeBlockRequest request = new TimeBlockRequest(null, START, END, 60, TODAY);
        when(timeBlockRepository.save(any())).thenReturn(timeBlock);

        TimeBlockResponse result = service.createTimeBlock(request, USER_ID);

        assertEquals(BLOCK_ID, result.id());
        assertEquals(USER_ID, result.userId());
        assertEquals(60, result.estimatedMinutes());
        verify(timeBlockRepository).save(any(TimeBlock.class));
    }

    @Test
    void getTimeBlocksByDate_returnsList() {
        when(timeBlockRepository.findByUserIdAndDate(USER_ID, TODAY)).thenReturn(List.of(timeBlock));

        List<TimeBlockResponse> result = service.getTimeBlocksByDate(TODAY, USER_ID);

        assertEquals(1, result.size());
        assertEquals(BLOCK_ID, result.get(0).id());
        assertEquals(TODAY, result.get(0).date());
    }

    @Test
    void createWeeklyPlan_savesAndReturnsResponse() {
        WeeklyPlanRequest request = new WeeklyPlanRequest(TODAY, TODAY.plusDays(6));
        when(weeklyPlanRepository.findByUserIdAndWeekStartDate(USER_ID, TODAY)).thenReturn(Optional.empty());
        when(weeklyPlanRepository.save(any())).thenReturn(weeklyPlan);

        WeeklyPlanResponse result = service.createWeeklyPlan(request, USER_ID);

        assertEquals(PLAN_ID, result.id());
        assertEquals(ScheduleStatus.OPEN, result.status());
        verify(weeklyPlanRepository).save(any(WeeklyPlan.class));
    }

    @Test
    void createWeeklyPlan_semanaJaAberta_devolveOMesmoPlanoSemDuplicar() {
        WeeklyPlanRequest request = new WeeklyPlanRequest(TODAY, TODAY.plusDays(6));
        when(weeklyPlanRepository.findByUserIdAndWeekStartDate(USER_ID, TODAY)).thenReturn(Optional.of(weeklyPlan));

        WeeklyPlanResponse result = service.createWeeklyPlan(request, USER_ID);

        assertEquals(PLAN_ID, result.id());
        verify(weeklyPlanRepository, never()).save(any());
    }

    @Test
    void closeWeeklyPlan_setsStatusClosed() {
        WeeklyPlan closed = WeeklyPlan.builder()
                .id(PLAN_ID).userId(USER_ID)
                .weekStartDate(TODAY).weekEndDate(TODAY.plusDays(6))
                .status(ScheduleStatus.CLOSED)
                .build();
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(timeBlock));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(taskReportClient.getReport(AUTH_HEADER, TODAY, TODAY.plusDays(6)))
                .thenReturn(Optional.of(new TaskReport(5, 3, 4_500L, 150L)));
        when(weeklySummaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(weeklyPlanRepository.save(any())).thenReturn(closed);

        WeeklyPlanResponse result = service.closeWeeklyPlan(PLAN_ID, USER_ID, AUTH_HEADER);

        assertEquals(ScheduleStatus.CLOSED, result.status());
        // O retrato tem de ser gravado ANTES do fechamento, senão a semana fecha vazia
        verify(weeklySummaryRepository).save(any(WeeklySummary.class));
    }

    @Test
    void closeWeeklyPlan_semRelatorio_naoFechaUmaSemanaSemSnapshotCompleto() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(timeBlock));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(taskReportClient.getReport(AUTH_HEADER, TODAY, TODAY.plusDays(6))).thenReturn(Optional.empty());
        when(weeklySummaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertThrows(IllegalStateException.class,
                () -> service.closeWeeklyPlan(PLAN_ID, USER_ID, AUTH_HEADER));

        assertEquals(ScheduleStatus.OPEN, weeklyPlan.getStatus());
        verify(weeklyPlanRepository, never()).save(any());
    }

    @Test
    void closeWeeklyPlan_jaFechada_naoRegeraOResumo() {
        weeklyPlan.setStatus(ScheduleStatus.CLOSED);
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));

        WeeklyPlanResponse result = service.closeWeeklyPlan(PLAN_ID, USER_ID, AUTH_HEADER);

        assertEquals(ScheduleStatus.CLOSED, result.status());
        verify(weeklySummaryRepository, never()).save(any());
        verify(weeklyPlanRepository, never()).save(any());
    }

    @Test
    void closeWeeklyPlan_notFound_throwsException() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.closeWeeklyPlan(PLAN_ID, USER_ID, AUTH_HEADER));
    }

    @Test
    void generateWeeklySummary_semRelatorio_marcaDadosParciaisSemConfundirBlocosComTarefas() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(timeBlock));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(taskReportClient.getReport(AUTH_HEADER, TODAY, TODAY.plusDays(6))).thenReturn(Optional.empty());
        when(weeklySummaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklySummaryResponse result = service.generateWeeklySummary(PLAN_ID, USER_ID, AUTH_HEADER);

        assertEquals(60, result.totalScheduledMinutes()); // veio do bloco no calendário
        assertEquals(0, result.totalTasks());
        assertEquals(0L, result.totalActualSeconds());
        assertEquals(0, result.completedTasks());
        assertEquals(SummaryDataStatus.PARTIAL, result.dataStatus());
    }

    @Test
    void generateWeeklySummary_comRelatorio_preencheDadosReais() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(timeBlock));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());
        when(taskReportClient.getReport(AUTH_HEADER, TODAY, TODAY.plusDays(6)))
                .thenReturn(Optional.of(new TaskReport(5, 3, 4_500L, 150L)));
        when(weeklySummaryRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WeeklySummaryResponse result = service.generateWeeklySummary(PLAN_ID, USER_ID, AUTH_HEADER);

        // As três grandezas são distintas: 60 min na agenda, 150 min estimados
        // nas tarefas, 4500s executados. Nenhuma substitui a outra.
        assertEquals(60, result.totalScheduledMinutes());
        assertEquals(150, result.totalEstimatedMinutes());
        assertEquals(4_500L, result.totalActualSeconds());
        assertEquals(5, result.totalTasks());
        assertEquals(3, result.completedTasks());
        // 4500s executados - 60min agendados (3600s) = 900s de desvio
        assertEquals(900L, result.deviationSeconds());
        verify(weeklySummaryRepository).save(argThat(summary ->
                summary.getAnalyticsPayload() != null
                        && summary.getAnalyticsPayload().version() == WeeklyAnalyticsPayload.CURRENT_VERSION
                        && summary.getAnalyticsPayload().timeBlocks().size() == 1));
    }

    @Test
    void getWeeklyAnalytics_semanaAberta_reuneRelatorioEAgendaAoVivo() {
        TaskReport report = new TaskReport(2, 1, 3_600L, 120L);
        when(weeklyPlanRepository.findByUserIdAndWeekStartDate(USER_ID, TODAY))
                .thenReturn(Optional.of(weeklyPlan));
        when(taskReportClient.getReport(AUTH_HEADER, TODAY, TODAY.plusDays(6)))
                .thenReturn(Optional.of(report));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of(timeBlock));

        WeeklyAnalyticsResponse result = service.getWeeklyAnalytics(TODAY, USER_ID, AUTH_HEADER);

        assertEquals("LIVE", result.source());
        assertEquals(ScheduleStatus.OPEN, result.status());
        assertEquals(2, result.report().totalTasks());
        assertEquals(1, result.timeBlocks().size());
    }

    @Test
    void getWeeklyAnalytics_semanaFechada_usaSnapshotSemConsultarDadosMutaveis() {
        weeklyPlan.setStatus(ScheduleStatus.CLOSED);
        TaskReport congelado = new TaskReport(7, 5, 4_500L, 600L);
        WeeklySummary summary = WeeklySummary.builder()
                .weeklyPlan(weeklyPlan)
                .dataStatus(SummaryDataStatus.COMPLETE)
                .analyticsPayload(new WeeklyAnalyticsPayload(
                        WeeklyAnalyticsPayload.CURRENT_VERSION,
                        congelado,
                        List.of(new TimeBlockResponse(BLOCK_ID, USER_ID, null, START, END, 60, TODAY))))
                .build();
        when(weeklyPlanRepository.findByUserIdAndWeekStartDate(USER_ID, TODAY))
                .thenReturn(Optional.of(weeklyPlan));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(summary));

        WeeklyAnalyticsResponse result = service.getWeeklyAnalytics(TODAY, USER_ID, AUTH_HEADER);

        assertEquals("SNAPSHOT", result.source());
        assertEquals(ScheduleStatus.CLOSED, result.status());
        assertEquals(7, result.report().totalTasks());
        verifyNoInteractions(taskReportClient, timeBlockRepository);
    }

    @Test
    void getWeeklyAnalytics_snapshotAntigo_reconstroiComoParcial() {
        weeklyPlan.setStatus(ScheduleStatus.CLOSED);
        WeeklySummary antigo = WeeklySummary.builder().weeklyPlan(weeklyPlan).build();
        TaskReport reconstruido = new TaskReport(1, 0, 0, 30);
        when(weeklyPlanRepository.findByUserIdAndWeekStartDate(USER_ID, TODAY))
                .thenReturn(Optional.of(weeklyPlan));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(antigo));
        when(taskReportClient.getReport(AUTH_HEADER, TODAY, TODAY.plusDays(6)))
                .thenReturn(Optional.of(reconstruido));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, TODAY, TODAY.plusDays(6)))
                .thenReturn(List.of());

        WeeklyAnalyticsResponse result = service.getWeeklyAnalytics(TODAY, USER_ID, AUTH_HEADER);

        assertEquals("RECONSTRUCTED", result.source());
        assertEquals(SummaryDataStatus.PARTIAL, result.dataStatus());
    }

    @Test
    void generateWeeklySummary_semanaFechada_devolveORetratoCongelado() {
        weeklyPlan.setStatus(ScheduleStatus.CLOSED);
        WeeklySummary congelado = WeeklySummary.builder()
                .weeklyPlan(weeklyPlan)
                .totalScheduledMinutes(120).totalEstimatedMinutes(90)
                .totalActualSeconds(7_200L).deviationSeconds(0L)
                .completedTasks(4).totalTasks(4)
                .build();
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(congelado));

        WeeklySummaryResponse result = service.generateWeeklySummary(PLAN_ID, USER_ID, AUTH_HEADER);

        assertEquals(120, result.totalScheduledMinutes());
        assertEquals(4, result.completedTasks());
        // Nada de recalcular: nem blocos, nem task-service, nem escrita
        verify(timeBlockRepository, never()).findByUserIdAndDateBetween(any(), any(), any());
        verify(taskReportClient, never()).getReport(any(), any(), any());
        verify(weeklySummaryRepository, never()).save(any());
    }

    @Test
    void findWeeklySummary_leOSalvoSemRecalcular() {
        WeeklySummary salvo = WeeklySummary.builder()
                .weeklyPlan(weeklyPlan).totalScheduledMinutes(45).build();
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.of(salvo));

        Optional<WeeklySummaryResponse> result = service.findWeeklySummary(PLAN_ID, USER_ID);

        assertTrue(result.isPresent());
        assertEquals(45, result.get().totalScheduledMinutes());
        verify(taskReportClient, never()).getReport(any(), any(), any());
        verify(weeklySummaryRepository, never()).save(any());
    }

    @Test
    void findWeeklySummary_semResumoAinda_devolveVazio() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.of(weeklyPlan));
        when(weeklySummaryRepository.findByWeeklyPlanId(PLAN_ID)).thenReturn(Optional.empty());

        assertTrue(service.findWeeklySummary(PLAN_ID, USER_ID).isEmpty());
    }

    @Test
    void findWeeklyPlan_achaPelaDataDeInicio() {
        when(weeklyPlanRepository.findByUserIdAndWeekStartDate(USER_ID, TODAY)).thenReturn(Optional.of(weeklyPlan));

        assertEquals(PLAN_ID, service.findWeeklyPlan(TODAY, USER_ID).orElseThrow().id());
    }

    @Test
    void generateWeeklySummary_planNotFound_throwsException() {
        when(weeklyPlanRepository.findByIdAndUserId(PLAN_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.generateWeeklySummary(PLAN_ID, USER_ID, AUTH_HEADER));
    }

    @Test
    void getOverallAnalytics_dividePeriodosLongosEmAte92Dias() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 4, 11);
        TaskReport vazio = new TaskReport(0, 0, 0, 0);
        when(taskReportClient.getReport(AUTH_HEADER, from, from.plusDays(91)))
                .thenReturn(Optional.of(vazio));
        when(taskReportClient.getReport(AUTH_HEADER, from.plusDays(92), to))
                .thenReturn(Optional.of(vazio));
        when(timeBlockRepository.findByUserIdAndDateBetween(USER_ID, from, to))
                .thenReturn(List.of(timeBlock));

        AnalyticsOverallResponse result = service.getOverallAnalytics(from, to, USER_ID, AUTH_HEADER);

        assertEquals(2, result.reports().size());
        assertEquals(1, result.timeBlocks().size());
        verify(taskReportClient).getReport(AUTH_HEADER, from, from.plusDays(91));
        verify(taskReportClient).getReport(AUTH_HEADER, from.plusDays(92), to);
    }

    @Test
    void getOverallAnalytics_falhaSemEntregarHistoricoParcial() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 1, 31);
        when(taskReportClient.getReport(AUTH_HEADER, from, to)).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class,
                () -> service.getOverallAnalytics(from, to, USER_ID, AUTH_HEADER));
        verifyNoInteractions(timeBlockRepository);
    }

    @Test
    void overlaps_whenBlocksOverlap_returnsTrue() {
        TimeBlock a = TimeBlock.builder().startDateTime(START).endDateTime(END).build();
        TimeBlock b = TimeBlock.builder()
                .startDateTime(START.plusMinutes(30)).endDateTime(END.plusMinutes(30))
                .build();

        assertTrue(service.overlaps(a, b));
    }

    @Test
    void overlaps_whenBlocksDoNotOverlap_returnsFalse() {
        TimeBlock a = TimeBlock.builder().startDateTime(START).endDateTime(END).build();
        TimeBlock b = TimeBlock.builder()
                .startDateTime(END).endDateTime(END.plusHours(1))
                .build();

        assertFalse(service.overlaps(a, b));
    }
}
