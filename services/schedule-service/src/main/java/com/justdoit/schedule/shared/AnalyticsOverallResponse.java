package com.justdoit.schedule.shared;

import com.justdoit.schedule.integration.TaskReportClient.TaskReport;

import java.time.LocalDate;
import java.util.List;

/** Dados históricos reunidos pelo schedule-service em uma única requisição. */
public record AnalyticsOverallResponse(
        LocalDate from,
        LocalDate to,
        List<TaskReport> reports,
        List<TimeBlockResponse> timeBlocks
) { }
