package com.justdoit.schedule.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record TimeBlockResponse(
    UUID id,
    UUID userId,
    UUID taskId,
    LocalDateTime startDateTime,
    LocalDateTime endDateTime,
    Integer estimatedMinutes,
    LocalDate date
) {}
