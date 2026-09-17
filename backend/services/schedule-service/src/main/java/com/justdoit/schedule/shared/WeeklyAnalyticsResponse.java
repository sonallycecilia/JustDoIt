package com.justdoit.schedule.shared;

import com.justdoit.schedule.integration.TaskReportClient.TaskReport;

import java.time.LocalDate;
import java.util.List;

/**
 * Contrato único da análise de uma semana.
 *
 * OPEN/LIVE é recalculado; CLOSED/SNAPSHOT vem do retrato persistido no
 * fechamento. RECONSTRUCTED identifica planos antigos, criados antes do payload
 * analítico existir, cujos dados só podem ser reconstruídos parcialmente.
 */
public record WeeklyAnalyticsResponse(
        LocalDate from,
        LocalDate to,
        ScheduleStatus status,
        String source,
        SummaryDataStatus dataStatus,
        TaskReport report,
        List<TimeBlockResponse> timeBlocks
) { }
