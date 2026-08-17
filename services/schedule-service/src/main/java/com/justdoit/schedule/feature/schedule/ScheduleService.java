package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.integration.TaskReportClient;
import com.justdoit.schedule.integration.TaskReportClient.TaskReport;
import com.justdoit.schedule.shared.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ScheduleService {

    private final TimeBlockRepository timeBlockRepository;
    private final WeeklyPlanRepository weeklyPlanRepository;
    private final WeeklySummaryRepository weeklySummaryRepository;
    private final TaskReportClient taskReportClient;

    @Transactional
    public TimeBlockResponse createTimeBlock(TimeBlockRequest request, UUID userId) {
        TimeBlock block = TimeBlock.builder()
                .userId(userId)
                .taskId(request.taskId())
                .startDateTime(request.startDateTime())
                .endDateTime(request.endDateTime())
                .estimatedMinutes(request.estimatedMinutes())
                .date(request.date())
                .build();
        return toTimeBlockResponse(timeBlockRepository.save(block));
    }

    public List<TimeBlockResponse> getTimeBlocksByDate(LocalDate date, UUID userId) {
        return timeBlockRepository.findByUserIdAndDate(userId, date).stream()
                .map(this::toTimeBlockResponse)
                .toList();
    }

    public List<TimeBlockResponse> getTimeBlocksBetween(LocalDate from, LocalDate to, UUID userId) {
        return timeBlockRepository.findByUserIdAndDateBetween(userId, from, to).stream()
                .map(this::toTimeBlockResponse)
                .toList();
    }

    @Transactional
    public TimeBlockResponse updateTimeBlock(UUID blockId, TimeBlockRequest request, UUID userId) {
        TimeBlock block = timeBlockRepository.findByIdAndUserId(blockId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Time block not found"));
        block.setTaskId(request.taskId());
        block.setStartDateTime(request.startDateTime());
        block.setEndDateTime(request.endDateTime());
        block.setEstimatedMinutes(request.estimatedMinutes());
        block.setDate(request.date());
        return toTimeBlockResponse(timeBlockRepository.save(block));
    }

    @Transactional
    public void deleteTimeBlock(UUID blockId, UUID userId) {
        TimeBlock block = timeBlockRepository.findByIdAndUserId(blockId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Time block not found"));
        timeBlockRepository.delete(block);
    }

    /**
     * Abre o plano da semana, ou devolve o que já existe.
     *
     * Idempotente de propósito: o frontend chama isto toda vez que a Análise
     * carrega, e criar um plano novo a cada visita encheria o banco de semanas
     * duplicadas, cada uma com o seu resumo.
     */
    @Transactional
    public WeeklyPlanResponse createWeeklyPlan(WeeklyPlanRequest request, UUID userId) {
        return weeklyPlanRepository.findByUserIdAndWeekStartDate(userId, request.weekStartDate())
                .map(this::toWeeklyPlanResponse)
                .orElseGet(() -> toWeeklyPlanResponse(weeklyPlanRepository.save(WeeklyPlan.builder()
                        .userId(userId)
                        .weekStartDate(request.weekStartDate())
                        .weekEndDate(request.weekEndDate())
                        .status(ScheduleStatus.OPEN)
                        .build())));
    }

    /** Plano de uma semana pelo dia de início (o frontend conhece a data, não o id). */
    public Optional<WeeklyPlanResponse> findWeeklyPlan(LocalDate weekStartDate, UUID userId) {
        return weeklyPlanRepository.findByUserIdAndWeekStartDate(userId, weekStartDate)
                .map(this::toWeeklyPlanResponse);
    }

    public List<WeeklyPlanResponse> findWeeklyPlans(LocalDate from, LocalDate to, UUID userId) {
        if (from == null || to == null || to.isBefore(from)) {
            throw new IllegalArgumentException("Período inválido");
        }
        return weeklyPlanRepository
                .findByUserIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(userId, from, to)
                .stream().map(this::toWeeklyPlanResponse).toList();
    }

    /**
     * Reúne todo o histórico em uma chamada pública. Internamente o task-service
     * continua protegido pelo teto de 92 dias, por isso o intervalo é fatiado.
     * O token do próprio usuário é repassado em cada chamada.
     */
    public AnalyticsOverallResponse getOverallAnalytics(LocalDate from, LocalDate to, UUID userId,
                                                         String authorizationHeader) {
        if (from == null || to == null || to.isBefore(from) || to.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Período inválido");
        }

        List<TaskReport> reports = new java.util.ArrayList<>();
        LocalDate cursor = from;
        while (!cursor.isAfter(to)) {
            LocalDate chunkEnd = cursor.plusDays(91);
            if (chunkEnd.isAfter(to)) chunkEnd = to;
            TaskReport report = taskReportClient.getReport(authorizationHeader, cursor, chunkEnd)
                    .orElseThrow(() -> new IllegalStateException("Task report unavailable"));
            reports.add(report);
            cursor = chunkEnd.plusDays(1);
        }

        List<TimeBlockResponse> blocks = getTimeBlocksBetween(from, to, userId);
        return new AnalyticsOverallResponse(from, to, reports, blocks);
    }

    /**
     * Fonte única da tela semanal. Uma semana fechada nunca volta a consultar
     * entidades mutáveis; semanas abertas continuam refletindo o estado atual.
     * Planos históricos anteriores à migração são reconstruídos e marcados como
     * PARTIAL para não se passarem por um retrato fiel.
     */
    public WeeklyAnalyticsResponse getWeeklyAnalytics(LocalDate weekStart, UUID userId,
                                                       String authorizationHeader) {
        if (weekStart == null) throw new IllegalArgumentException("Semana inválida");
        LocalDate weekEnd = weekStart.plusDays(6);
        Optional<WeeklyPlan> plan = weeklyPlanRepository.findByUserIdAndWeekStartDate(userId, weekStart);

        if (plan.isPresent() && plan.get().getStatus() == ScheduleStatus.CLOSED) {
            Optional<WeeklySummary> summary = weeklySummaryRepository.findByWeeklyPlanId(plan.get().getId());
            if (summary.isPresent() && summary.get().getAnalyticsPayload() != null) {
                WeeklyAnalyticsPayload payload = summary.get().getAnalyticsPayload();
                SummaryDataStatus snapshotStatus = summary.get().getDataStatus() != null
                        ? summary.get().getDataStatus()
                        : SummaryDataStatus.PARTIAL;
                return new WeeklyAnalyticsResponse(weekStart, weekEnd, ScheduleStatus.CLOSED,
                        "SNAPSHOT", snapshotStatus, payload.report(), payload.timeBlocks());
            }
            return liveWeeklyAnalytics(weekStart, weekEnd, userId, authorizationHeader,
                    ScheduleStatus.CLOSED, "RECONSTRUCTED", SummaryDataStatus.PARTIAL);
        }

        return liveWeeklyAnalytics(weekStart, weekEnd, userId, authorizationHeader,
                ScheduleStatus.OPEN, "LIVE", SummaryDataStatus.COMPLETE);
    }

    private WeeklyAnalyticsResponse liveWeeklyAnalytics(LocalDate from, LocalDate to, UUID userId,
                                                        String authorizationHeader, ScheduleStatus status,
                                                        String source, SummaryDataStatus dataStatus) {
        TaskReport report = taskReportClient.getReport(authorizationHeader, from, to)
                .orElseThrow(() -> new IllegalStateException("Task report unavailable"));
        return new WeeklyAnalyticsResponse(from, to, status, source, dataStatus, report,
                getTimeBlocksBetween(from, to, userId));
    }

    /**
     * Fecha a semana. Gera o resumo uma última vez ANTES de marcar CLOSED: é esse
     * retrato que fica congelado. Sem isso, fechar uma semana sem nunca ter
     * aberto a Análise deixaria um plano fechado com resumo zerado.
     */
    @Transactional
    public WeeklyPlanResponse closeWeeklyPlan(UUID planId, UUID userId, String authorizationHeader) {
        WeeklyPlan plan = weeklyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Weekly plan not found"));
        if (plan.getStatus() != ScheduleStatus.CLOSED) {
            WeeklySummaryResponse snapshot = generateWeeklySummary(planId, userId, authorizationHeader);
            if (snapshot.dataStatus() != SummaryDataStatus.COMPLETE) {
                // Um plano CLOSED sem o payload integral voltaria a depender de
                // dados mutáveis. Mantém OPEN para que a orquestração possa ser
                // repetida quando o task-service voltar.
                throw new IllegalStateException("Weekly analytics snapshot unavailable");
            }
            plan.setStatus(ScheduleStatus.CLOSED);
            plan = weeklyPlanRepository.save(plan);
        }
        return toWeeklyPlanResponse(plan);
    }

    /** Resumo já salvo, sem recalcular nada. Vazio quando a semana nunca foi resumida. */
    public Optional<WeeklySummaryResponse> findWeeklySummary(UUID planId, UUID userId) {
        weeklyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Weekly plan not found"));
        return weeklySummaryRepository.findByWeeklyPlanId(planId).map(this::toWeeklySummaryResponse);
    }

    @Transactional
    public WeeklySummaryResponse generateWeeklySummary(UUID planId, UUID userId, String authorizationHeader) {
        WeeklyPlan plan = weeklyPlanRepository.findByIdAndUserId(planId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Weekly plan not found"));

        // Semana fechada é retrato, não painel ao vivo: recalcular aqui apagaria
        // justamente o que o fechamento quis preservar.
        if (plan.getStatus() == ScheduleStatus.CLOSED) {
            Optional<WeeklySummary> congelado = weeklySummaryRepository.findByWeeklyPlanId(planId);
            if (congelado.isPresent()) {
                return toWeeklySummaryResponse(congelado.get());
            }
        }

        List<TimeBlock> blocks = timeBlockRepository.findByUserIdAndDateBetween(
                userId, plan.getWeekStartDate(), plan.getWeekEndDate()
        );

        int totalScheduled = blocks.stream()
                .mapToInt(b -> b.getEstimatedMinutes() != null ? b.getEstimatedMinutes() : 0)
                .sum();

        WeeklySummary summary = weeklySummaryRepository.findByWeeklyPlanId(planId)
                .orElse(WeeklySummary.builder().weeklyPlan(plan).build());

        summary.setTotalScheduledMinutes(totalScheduled);

        // Tarefas, tempo executado e estimativa vêm do task-service, com o token do
        // próprio usuário. Sem resposta, o resumo sai só com o agendado local e a
        // contagem de blocos — nunca falha por causa do outro serviço.
        Optional<TaskReport> report = taskReportClient.getReport(
                authorizationHeader, plan.getWeekStartDate(), plan.getWeekEndDate());
        if (report.isPresent()) {
            TaskReport r = report.get();
            summary.setTotalTasks((int) r.totalTasks());
            summary.setCompletedTasks((int) r.completedTasks());
            summary.setTotalActualSeconds(r.totalActualSeconds());
            summary.setTotalEstimatedMinutes((int) r.totalEstimatedMinutes());
            summary.setDeviationSeconds(r.totalActualSeconds() - totalScheduled * 60L);
            summary.setDataStatus(SummaryDataStatus.COMPLETE);
            summary.setAnalyticsPayload(new WeeklyAnalyticsPayload(
                    WeeklyAnalyticsPayload.CURRENT_VERSION,
                    r,
                    blocks.stream().map(this::toTimeBlockResponse).toList()
            ));
        } else {
            // Não substitui tarefas por blocos: são entidades diferentes. O zero
            // vem acompanhado de PARTIAL para nunca parecer um retrato completo.
            summary.setTotalTasks(0);
            summary.setDataStatus(SummaryDataStatus.PARTIAL);
            summary.setAnalyticsPayload(null);
        }

        return toWeeklySummaryResponse(weeklySummaryRepository.save(summary));
    }

    public boolean overlaps(TimeBlock a, TimeBlock b) {
        return a.getStartDateTime().isBefore(b.getEndDateTime())
                && b.getStartDateTime().isBefore(a.getEndDateTime());
    }

    private TimeBlockResponse toTimeBlockResponse(TimeBlock b) {
        return new TimeBlockResponse(b.getId(), b.getUserId(), b.getTaskId(),
                b.getStartDateTime(), b.getEndDateTime(), b.getEstimatedMinutes(), b.getDate());
    }

    private WeeklyPlanResponse toWeeklyPlanResponse(WeeklyPlan p) {
        return new WeeklyPlanResponse(p.getId(), p.getUserId(),
                p.getWeekStartDate(), p.getWeekEndDate(), p.getStatus());
    }

    private WeeklySummaryResponse toWeeklySummaryResponse(WeeklySummary s) {
        return new WeeklySummaryResponse(s.getId(), s.getWeeklyPlan().getId(),
                s.getTotalScheduledMinutes(), s.getTotalEstimatedMinutes(),
                s.getTotalActualSeconds(), s.getDeviationSeconds(),
                s.getCompletedTasks(), s.getTotalTasks(),
                s.getDataStatus() != null ? s.getDataStatus() : SummaryDataStatus.PARTIAL);
    }
}
