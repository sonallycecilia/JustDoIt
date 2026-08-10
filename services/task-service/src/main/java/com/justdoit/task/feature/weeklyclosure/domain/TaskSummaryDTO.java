package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.shared.TaskStatus;
import java.util.UUID;

public record TaskSummaryDTO(
    UUID taskId,
    String title,
    TaskStatus status,
    Integer estimatedMinutes
) {}