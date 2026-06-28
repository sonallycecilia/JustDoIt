package com.justdoit.task.shared;

import java.time.LocalDateTime;

public record FocusSessionRequest(
    Integer focusMinutes,
    Integer breakMinutes,
    SessionType sessionType,
    LocalDateTime startedAt,
    LocalDateTime endedAt,
    Boolean completed
) {}
