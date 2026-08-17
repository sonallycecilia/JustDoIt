package com.justdoit.task.feature.weeklyclosure.domain;

import java.util.List;
import java.time.LocalDate;
import java.util.UUID;

public record ClosurePreviewDTO(
    UUID cycleId,
    LocalDate weekStartDate,
    LocalDate weekEndDate,
    List<TaskSummaryDTO> pendingTasks,
    List<TaskSummaryDTO> completedTasks
) {}
