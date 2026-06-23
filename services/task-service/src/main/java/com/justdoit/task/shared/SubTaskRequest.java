package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubTaskRequest(
    @NotBlank @Size(max = 200) String title,
    Integer position
) {}
