package com.justdoit.task.feature.weeklyclosure.domain;

import java.util.List;
import java.util.UUID;

public record ClosurePreviewDTO(
    UUID cycleId,
    List<TaskSummaryDTO> pendingTasks,
    List<TaskSummaryDTO> completedTasks
) {}