package com.justdoit.task.feature.export;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExportJobAcceptedResponse(
        UUID jobId,
        ExportJobStatus status,
        String statusUrl,
        LocalDateTime acceptedAt
) { }
