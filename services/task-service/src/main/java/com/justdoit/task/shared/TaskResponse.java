package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    UUID userId,
    UUID categoryId,
    String title,
    String description,
    TaskStatus status,
    Priority priority,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
