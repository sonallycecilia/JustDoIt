package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.integration.TaskReportClient.TaskReport;
import com.justdoit.schedule.shared.TimeBlockResponse;

import java.util.List;

/** Payload versionado persistido junto do resumo semanal. */
public record WeeklyAnalyticsPayload(
        int version,
        TaskReport report,
        List<TimeBlockResponse> timeBlocks
) {
    public static final int CURRENT_VERSION = 1;
}
