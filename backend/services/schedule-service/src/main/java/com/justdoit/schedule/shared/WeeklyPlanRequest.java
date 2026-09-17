package com.justdoit.schedule.shared;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WeeklyPlanRequest(
    @NotNull LocalDate weekStartDate,
    @NotNull LocalDate weekEndDate
) {}
