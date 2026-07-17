package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserNoteResponse(
    UUID id,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}