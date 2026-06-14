package com.justdoit.task.shared;

import java.util.UUID;

public record CategoryResponse(
    UUID id,
    UUID userId,
    String name,
    String color,
    String description
) {}
