package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record TaskRequest(
    @NotBlank String title,
    String description,
    UUID categoryId,
    Priority priority
) {}
