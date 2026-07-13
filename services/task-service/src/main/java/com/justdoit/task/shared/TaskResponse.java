package com.justdoit.task.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

public record TaskResponse(
    UUID id,
    UUID userId,
    UUID categoryId,
    String title,
    String description,
    Integer estimatedMinutes,
    TaskStatus status,
    Priority priority,
    LocalDate dueDate,
    LocalTime dueTime,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
