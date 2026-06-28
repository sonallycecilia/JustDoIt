package com.justdoit.task.shared;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record TaskNoteRequest(
    @NotBlank @Size(max = 5000) String content
) {}
