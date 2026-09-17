package com.justdoit.task.feature.export;

import com.justdoit.task.integration.NotificationClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TaskExportWorker {

    private final ExportJobRepository jobRepository;
    private final ExportFileStorage storage;
    private final TaskExportStreamingWriter writer;
    private final TaskExportService legacyExportService;
    private final ExportProperties properties;
    private final ExportMetrics metrics;
    private final ExportJobLinks links;
    private final NotificationClient notificationClient;

    @Async("exportExecutor")
    public void process(UUID jobId) {
        ExportJob job = jobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != ExportJobStatus.PENDING) return;

        Instant started = Instant.now();
        Path path = null;
        metrics.started();
        try {
            job.setStatus(ExportJobStatus.RUNNING);
            job.setStartedAt(LocalDateTime.now(ZoneOffset.UTC));
            jobRepository.save(job);

            ExportFileStorage.StoredExport target = storage.allocate(jobId, job.getFormat());
            path = target.path();
            ExportGenerationResult result = writer.write(job.getUserId(), job.getFormat(), path);

            LocalDateTime completedAt = LocalDateTime.now(ZoneOffset.UTC);
            job.setStatus(ExportJobStatus.COMPLETED);
            job.setCompletedAt(completedAt);
            job.setDownloadExpiresAt(completedAt.plus(properties.getDownloadTtl()));
            job.setStorageKey(target.key());
            job.setFileName(legacyExportService.fileName(job.getFormat(), completedAt.toLocalDate()));
            job.setRecordCount(result.recordCount());
            job.setFileSizeBytes(result.fileSizeBytes());
            job.setDurationMs(Duration.between(started, Instant.now()).toMillis());
            jobRepository.save(job);

            Duration elapsed = Duration.between(started, Instant.now());
            metrics.completed(elapsed, result);
            notificationClient.notifyExportReady(
                    job.getUserId(), links.downloadUrl(job), job.getDownloadExpiresAt());
        } catch (Exception error) {
            if (path != null) storage.delete(path.getFileName().toString());
            job.setStatus(ExportJobStatus.FAILED);
            job.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            job.setDurationMs(Duration.between(started, Instant.now()).toMillis());
            job.setErrorMessage(truncate(error.getMessage()));
            jobRepository.save(job);
            String reason = error instanceof ExportTimeoutException ? "timeout"
                    : error instanceof ExportLimitExceededException ? "limit" : "error";
            metrics.failed(Duration.between(started, Instant.now()), reason);
            log.error("Export job {} falhou: {}", jobId, error.getMessage(), error);
        }
    }

    private static String truncate(String message) {
        if (message == null || message.isBlank()) return "Falha inesperada ao gerar exportação";
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
