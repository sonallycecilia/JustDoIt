package com.justdoit.task.shared;

import com.justdoit.common.validation.TextoSeguro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubTaskRequest(
    @NotBlank @Size(max = 200) @TextoSeguro String title,
    Integer position
) {}
