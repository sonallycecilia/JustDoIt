package com.justdoit.schedule.integration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Cliente HTTP para o task-service (espelho do NotificationClient de lá).
 * Busca o agregado do período (GET /tasks/report) repassando o token do próprio
 * usuário — o endpoint é autenticado como qualquer outro, sem credencial especial.
 *
 * Best-effort: qualquer falha vira Optional.empty() e o resumo semanal sai só
 * com os dados locais (planejado) — um task-service fora do ar não derruba a
 * geração do resumo.
 */
@Slf4j
@Component
public class TaskReportClient {

    /**
     * Recorte do TaskReportResponse do task-service com o que o resumo usa
     * (from/to/byDay são ignorados na desserialização).
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TaskReport(LocalDate from, LocalDate to, long totalTasks, long completedTasks,
                             long totalActualSeconds, long totalEstimatedMinutes,
                             List<DaySummary> byDay, long dueTasksCompleted,
                             long completedInPeriod, long overdueOpenTasks,
                             long undatedOpenTasks, long undatedCompletedTasks,
                             long undatedEstimatedMinutes, long totalMeasuredSeconds,
                             long totalInferredSeconds, List<CategorySummary> byCategory,
                             List<TaskPerformance> taskPerformance) {
        public TaskReport(long totalTasks, long completedTasks, long totalActualSeconds,
                          long totalEstimatedMinutes) {
            this(null, null, totalTasks, completedTasks, totalActualSeconds, totalEstimatedMinutes,
                    List.of(), 0, completedTasks, 0, 0, 0, 0,
                    totalActualSeconds, 0, List.of(), List.of());
        }

        public record DaySummary(LocalDate date, long actualSeconds, long focusSeconds,
                                 long timerSeconds, long completedTasks, long focusSessions,
                                 long estimatedMinutes, long measuredSeconds, long inferredSeconds) { }
        public record CategorySummary(UUID categoryId, String categoryName, String categoryColor,
                                      long estimatedMinutes, long measuredSeconds, long inferredSeconds,
                                      long dueTasks, long dueTasksCompleted) { }
        public record TaskPerformance(UUID taskId, String title, long estimatedMinutes,
                                      long actualSeconds, long deviationSeconds) { }
    }

    private final RestClient restClient;

    public TaskReportClient(@Value("${app.task-service.url:http://localhost:8081}") String baseUrl) {
        // Timeouts curtos: a chamada roda dentro da request do usuário (e da
        // transação do resumo) — um task-service lento não pode segurar.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public Optional<TaskReport> getReport(String authorizationHeader, LocalDate from, LocalDate to) {
        try {
            TaskReport report = restClient.get()
                    .uri(uri -> uri.path("/tasks/report")
                            .queryParam("from", from)
                            .queryParam("to", to)
                            .build())
                    .header("Authorization", authorizationHeader)
                    .retrieve()
                    .body(TaskReport.class);
            return Optional.ofNullable(report);
        } catch (Exception e) {
            log.warn("Falha (ignorada) ao buscar relatório de tasks de {} a {}: {}", from, to, e.getMessage());
            return Optional.empty();
        }
    }
}
