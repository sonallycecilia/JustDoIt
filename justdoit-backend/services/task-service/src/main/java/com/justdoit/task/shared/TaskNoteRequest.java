package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;

public record TaskNoteRequest(
    @NotBlank String content
) {}
