package com.justdoit.task.shared;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TaskTimerLogRequest(
    @NotNull @Min(1) Long seconds
) {}
