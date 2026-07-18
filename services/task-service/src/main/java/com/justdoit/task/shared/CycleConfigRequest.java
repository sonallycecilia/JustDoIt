package com.justdoit.task.shared;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;

public record CycleConfigRequest(
    @NotNull CycleType cycleType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextResetDate,
    // Ciclo personalizado (obrigatórios só quando cycleType == CUSTOM;
    // validados no CycleConfigService).
    IntervalUnit intervalUnit,
    Integer intervalCount,
    Integer totalOccurrences,
    LocalTime startTime
) {}
