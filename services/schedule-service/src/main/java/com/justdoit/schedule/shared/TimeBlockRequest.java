package com.justdoit.schedule.shared;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TimeBlockRequest(
    UUID taskId,
    @NotNull LocalDateTime startDateTime,
    @NotNull LocalDateTime endDateTime,
    Integer estimatedMinutes,
    @NotNull LocalDate date
) {}
