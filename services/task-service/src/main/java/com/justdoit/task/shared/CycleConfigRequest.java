package com.justdoit.task.shared;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

public record CycleConfigRequest(
    @NotNull CycleType cycleType,
    LocalDate startDate,
    LocalDate endDate,
    LocalDate nextResetDate
) {}
