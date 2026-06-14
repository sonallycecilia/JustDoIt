package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;

public record SubTaskRequest(
    @NotBlank String title,
    Integer position
) {}
