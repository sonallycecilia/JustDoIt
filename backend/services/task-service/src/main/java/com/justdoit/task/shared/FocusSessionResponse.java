package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record FocusSessionResponse(
    UUID id,
    UUID taskId,
    Integer focusMinutes,
    Integer breakMinutes,
    SessionType sessionType,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Boolean completed
) {}
