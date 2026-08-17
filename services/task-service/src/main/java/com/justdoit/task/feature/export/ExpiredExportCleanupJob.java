package com.justdoit.task.feature.export;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Component
@RequiredArgsConstructor
public class ExpiredExportCleanupJob {

    private final ExportJobRepository repository;
    private final ExportFileStorage storage;

    @Scheduled(fixedDelayString = "${app.export.cleanup-interval-ms:300000}")
    public void deleteExpiredFiles() {
        for (ExportJob job : repository.findByStatusAndDownloadExpiresAtBefore(
                ExportJobStatus.COMPLETED, LocalDateTime.now(ZoneOffset.UTC))) {
            storage.delete(job.getStorageKey());
            job.setStatus(ExportJobStatus.EXPIRED);
            job.setStorageKey(null);
            repository.save(job);
        }
    }
}
