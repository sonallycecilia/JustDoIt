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
    LocalDateTime updatedAt,
    UUID seriesId,
    CycleType cycleType,
    Integer reminderMinutesBefore
) {
    public TaskResponse(UUID id, UUID userId, UUID categoryId, String title, String description,
                        Integer estimatedMinutes, TaskStatus status, Priority priority,
                        LocalDate dueDate, LocalTime dueTime, LocalDateTime createdAt,
                        LocalDateTime updatedAt, UUID seriesId, CycleType cycleType) {
        this(id, userId, categoryId, title, description, estimatedMinutes, status, priority,
                dueDate, dueTime, createdAt, updatedAt, seriesId, cycleType, null);
    }
}
