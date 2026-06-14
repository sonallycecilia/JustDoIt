package com.justdoit.schedule.shared;

import java.util.UUID;

public record WeeklySummaryResponse(
    UUID id,
    UUID weeklyPlanId,
    Integer totalEstimatedMinutes,
    Long totalActualSeconds,
    Long deviationSeconds,
    Integer completedTasks,
    Integer totalTasks
) {}
