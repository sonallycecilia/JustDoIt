package com.justdoit.task.shared;

import java.util.UUID;

public record TaskModuleConfigResponse(
    UUID id,
    UUID taskId,
    Boolean focusEnabled,
    Boolean cycleEnabled,
    Boolean priorityEnabled,
    Boolean timerEnabled,
    Boolean notesEnabled
) {}
