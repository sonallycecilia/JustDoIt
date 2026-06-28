package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public record TaskRequest(
    @NotBlank @Size(max = 200) String title,
    @Size(max = 5000) String description,
    UUID categoryId,
    Priority priority,
    LocalDate dueDate,
    LocalTime dueTime
) {}
