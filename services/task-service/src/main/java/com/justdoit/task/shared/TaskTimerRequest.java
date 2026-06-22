package com.justdoit.task.shared;

import java.time.LocalDateTime;

public record TaskTimerRequest(
    Integer estimatedMinutes,
    Long actualSeconds,
    LocalDateTime completedAt
) {}
