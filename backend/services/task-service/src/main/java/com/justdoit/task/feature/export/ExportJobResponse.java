package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;

import java.time.LocalDateTime;
import java.util.UUID;

public record ExportJobResponse(
        UUID jobId,
        ExportFormat format,
        ExportJobStatus status,
        LocalDateTime createdAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt,
        LocalDateTime downloadExpiresAt,
        Long recordCount,
        Long fileSizeBytes,
        Long durationMs,
        String error,
        String downloadUrl
) { }
