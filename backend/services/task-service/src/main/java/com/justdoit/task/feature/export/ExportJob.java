package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "export_job")
public class ExportJob {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ExportFormat format;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private ExportJobStatus status;

    @Column(name = "storage_key")
    private String storageKey;

    @Column(name = "file_name")
    private String fileName;

    @Column(name = "record_count")
    private Long recordCount;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "download_expires_at")
    private LocalDateTime downloadExpiresAt;
}
