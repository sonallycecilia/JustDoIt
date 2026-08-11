package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record NoteResponse(
    UUID id,
    String title,
    String content,
    UUID categoryId,
    boolean pinned,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
