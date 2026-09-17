package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskTimerResponse(
    UUID id,
    UUID taskId,
    Integer estimatedMinutes,
    Long actualSeconds,
    LocalDateTime completedAt
) {}
