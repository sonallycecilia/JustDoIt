package com.justdoit.task.feature.weeklyclosure.domain;

import java.util.List;
import java.util.UUID;

public record ClosureCommandDTO(
    UUID cycleId,
    UUID userId,
    List<UUID> tasksToMigrate,
    List<UUID> tasksToArchive
) {}