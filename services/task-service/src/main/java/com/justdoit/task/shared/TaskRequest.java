package com.justdoit.task.shared;

import com.justdoit.common.validation.TextoSeguro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TaskRequest(
    @NotBlank @Size(max = 200) @TextoSeguro String title,
    @Size(max = 5000) @TextoSeguro String description,
    @PositiveOrZero(message = "A estimativa de tempo não pode ser negativa")
    Integer estimatedMinutes,
    UUID categoryId,
    Priority priority,
    LocalDate dueDate,
    LocalTime dueTime
) {}
