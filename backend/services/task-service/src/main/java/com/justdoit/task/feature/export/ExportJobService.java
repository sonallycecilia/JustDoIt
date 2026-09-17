package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ExportJobService {

    private static final List<ExportJobStatus> ACTIVE =
            List.of(ExportJobStatus.PENDING, ExportJobStatus.RUNNING);

    private final ExportJobRepository jobRepository;
    private final TaskExportPageReader pageReader;
    private final TaskExportWorker worker;
    private final ExportProperties properties;
    private final ExportMetrics metrics;
    private final ExportJobLinks links;
    private final ExportFileStorage storage;
    private final TemporaryDownloadTokenService tokenService;

    public synchronized ExportJobAcceptedResponse request(UUID userId, ExportFormat format) {
        long active = jobRepository.countByUserIdAndStatusIn(userId, ACTIVE);
        if (active >= properties.getMaxActivePerUser()) {
            metrics.rejected();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Já existe uma exportação em processamento para este usuário");
        }
        long records = pageReader.count(userId);
        if (records > properties.getMaxRecords()) {
            metrics.rejected();
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Quantidade de registros excede o limite de exportação");
        }

        ExportJob job = jobRepository.save(ExportJob.builder()
                .userId(userId)
                .format(format)
                .status(ExportJobStatus.PENDING)
                .build());
        metrics.accepted();
        try {
            worker.process(job.getId());
        } catch (TaskRejectedException rejected) {
            job.setStatus(ExportJobStatus.FAILED);
            job.setErrorMessage("Capacidade de exportação temporariamente esgotada");
            job.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC));
            jobRepository.save(job);
            metrics.rejected();
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Fila de exportação cheia; tente novamente mais tarde", rejected);
        }
        return new ExportJobAcceptedResponse(job.getId(), job.getStatus(),
                links.statusPath(job), job.getCreatedAt());
    }

    public ExportJobResponse status(UUID jobId, UUID userId) {
        return toResponse(findOwned(jobId, userId));
    }

    public ExportDownload download(UUID jobId, long expires, String token) {
        ExportJob job = jobRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (job.getStatus() == ExportJobStatus.EXPIRED) {
            throw new ResponseStatusException(HttpStatus.GONE, "Link expirado");
        }
        if (job.getStatus() != ExportJobStatus.COMPLETED || job.getStorageKey() == null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Exportação ainda não está disponível");
        }
        long storedExpiration = job.getDownloadExpiresAt().toEpochSecond(ZoneOffset.UTC);
        if (storedExpiration != expires
                || !tokenService.validate(jobId, job.getUserId(), expires, token)) {
            throw new ResponseStatusException(HttpStatus.GONE, "Link inválido ou expirado");
        }
        String contentType = job.getFormat() == ExportFormat.CSV
                ? "text/csv;charset=UTF-8" : "application/json";
        return new ExportDownload(storage.resource(job.getStorageKey()), job.getFileName(), contentType);
    }

    private ExportJob findOwned(UUID jobId, UUID userId) {
        return jobRepository.findByIdAndUserId(jobId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }

    private ExportJobResponse toResponse(ExportJob job) {
        String downloadUrl = job.getStatus() == ExportJobStatus.COMPLETED
                && job.getDownloadExpiresAt() != null ? links.downloadUrl(job) : null;
        return new ExportJobResponse(job.getId(), job.getFormat(), job.getStatus(),
                job.getCreatedAt(), job.getStartedAt(), job.getCompletedAt(),
                job.getDownloadExpiresAt(), job.getRecordCount(), job.getFileSizeBytes(),
                job.getDurationMs(), job.getErrorMessage(), downloadUrl);
    }
}
