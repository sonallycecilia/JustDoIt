package com.justdoit.task.feature.export;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExportJobRepository extends JpaRepository<ExportJob, UUID> {
    Optional<ExportJob> findByIdAndUserId(UUID id, UUID userId);

    long countByUserIdAndStatusIn(UUID userId, Collection<ExportJobStatus> statuses);

    List<ExportJob> findByStatusAndDownloadExpiresAtBefore(
            ExportJobStatus status, LocalDateTime expiration);
}
