package com.justdoit.task.shared;

import com.justdoit.common.validation.TextoSeguro;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    LocalTime dueTime,
    @Min(value = 1, message = "A antecedência do lembrete deve ser de ao menos 1 minuto")
    @Max(value = 525600, message = "A antecedência do lembrete não pode exceder 1 ano")
    Integer reminderMinutesBefore
) {
    public TaskRequest(String title, String description, Integer estimatedMinutes,
                       UUID categoryId, Priority priority, LocalDate dueDate, LocalTime dueTime) {
        this(title, description, estimatedMinutes, categoryId, priority, dueDate, dueTime, null);
    }
}
