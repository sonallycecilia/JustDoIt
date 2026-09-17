package com.justdoit.task.feature.export;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExpiredExportCleanupJobTest {

    @Mock private ExportJobRepository repository;
    @Mock private ExportFileStorage storage;

    @Test
    void expiredLinkDeletesFileAndBecomesGone() {
        ExportJob job = ExportJob.builder()
                .id(UUID.randomUUID())
                .status(ExportJobStatus.COMPLETED)
                .storageKey("job.csv")
                .downloadExpiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(repository.findByStatusAndDownloadExpiresAtBefore(
                any(), any())).thenReturn(List.of(job));

        new ExpiredExportCleanupJob(repository, storage).deleteExpiredFiles();

        verify(storage).delete("job.csv");
        assertThat(job.getStatus()).isEqualTo(ExportJobStatus.EXPIRED);
        assertThat(job.getStorageKey()).isNull();
        verify(repository).save(job);
    }
}
