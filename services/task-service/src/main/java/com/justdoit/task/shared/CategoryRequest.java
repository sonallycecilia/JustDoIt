package com.justdoit.task.shared;

import com.justdoit.common.validation.TextoSeguro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CategoryRequest(
    @NotBlank @Size(max = 100) @TextoSeguro String name,
    // color não recebe @TextoSeguro: é valor de cor (#RRGGBB), formato restrito.
    @NotBlank @Size(max = 30) String color,
    @Size(max = 500) @TextoSeguro String description
) {}
