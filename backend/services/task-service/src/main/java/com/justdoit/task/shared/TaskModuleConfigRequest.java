package com.justdoit.task.shared;

public record TaskModuleConfigRequest(
    Boolean focusEnabled,
    Boolean cycleEnabled,
    Boolean priorityEnabled,
    Boolean timerEnabled,
    Boolean notesEnabled
) {}
