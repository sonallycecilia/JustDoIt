package com.justdoit.task.shared;

import java.util.UUID;

public record SubTaskResponse(
    UUID id,
    String title,
    TaskStatus status,
    Integer position
) {}
