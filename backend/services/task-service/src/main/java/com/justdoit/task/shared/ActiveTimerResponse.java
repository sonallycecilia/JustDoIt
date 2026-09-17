package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record ActiveTimerResponse(
    UUID id,
    UUID taskId,
    LocalDateTime startedAt
) {}
