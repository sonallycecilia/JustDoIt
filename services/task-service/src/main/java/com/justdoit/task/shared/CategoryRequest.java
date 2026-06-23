package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank @Size(max = 100) String name,
    @NotBlank @Size(max = 30) String color,
    @Size(max = 500) String description
) {}
