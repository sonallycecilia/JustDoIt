package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskNoteResponse(
    UUID id,
    UUID taskId,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
