package com.justdoit.schedule.shared;

import java.time.LocalDate;
import java.util.UUID;

public record WeeklyPlanResponse(
    UUID id,
    UUID userId,
    LocalDate weekStartDate,
    LocalDate weekEndDate,
    ScheduleStatus status
) {}
