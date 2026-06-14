package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
    @NotBlank String name,
    @NotBlank String color,
    String description
) {}
