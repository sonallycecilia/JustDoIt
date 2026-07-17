package com.justdoit.task.shared;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record CycleConfigResponse(
    UUID id,
    UUID taskId,
    CycleType cycleType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextResetDate,
    IntervalUnit intervalUnit,
    Integer intervalCount,
    Integer totalOccurrences,
    LocalTime startTime
) {}
