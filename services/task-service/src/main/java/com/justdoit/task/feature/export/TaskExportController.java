package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class TaskExportController {

    private final ExportJobService exportJobService;

    /** Endpoint novo; registra a intenção e libera imediatamente a request. */
    @PostMapping("/exports")
    public ResponseEntity<ExportJobAcceptedResponse> requestExport(
            @RequestParam(required = false) String format,
            @AuthenticationPrincipal UUID userId) {
        ExportJobAcceptedResponse accepted = exportJobService.request(userId, parse(format));
        return ResponseEntity.accepted()
                .location(URI.create(accepted.statusUrl()))
                .body(accepted);
    }

    /** Compatibilidade: o GET antigo também passa a devolver 202, nunca o arquivo. */
    @GetMapping("/export")
    public ResponseEntity<ExportJobAcceptedResponse> requestExportLegacy(
            @RequestParam(required = false) String format,
            @AuthenticationPrincipal UUID userId) {
        return requestExport(format, userId);
    }

    @GetMapping("/exports/{jobId}")
    public ExportJobResponse status(@PathVariable UUID jobId,
                                    @AuthenticationPrincipal UUID userId) {
        return exportJobService.status(jobId, userId);
    }

    @GetMapping("/exports/{jobId}/download")
    public ResponseEntity<Resource> download(@PathVariable UUID jobId,
                                             @RequestParam long expires,
                                             @RequestParam String token) throws Exception {
        ExportDownload download = exportJobService.download(jobId, expires, token);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(download.fileName(), StandardCharsets.UTF_8).build());
        return ResponseEntity.ok()
                .headers(headers)
                .contentType(MediaType.parseMediaType(download.contentType()))
                .contentLength(download.resource().contentLength())
                .body(download.resource());
    }

    private static ExportFormat parse(String value) {
        try {
            return ExportFormat.from(value);
        } catch (IllegalArgumentException invalid) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST, invalid.getMessage());
        }
    }
}
