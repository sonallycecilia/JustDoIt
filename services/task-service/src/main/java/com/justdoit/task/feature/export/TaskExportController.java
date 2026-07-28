package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.TaskExportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Exportação dos dados do usuário neste serviço (portabilidade / backup local).
 * Fica sob /me como o UserDataController: são operações sobre "os meus dados",
 * não sobre um recurso identificado na URL.
 *
 * O usuário exportado é SEMPRE o dono do token (@AuthenticationPrincipal) —
 * não existe parâmetro de userId, justamente para que o cliente não consiga
 * pedir a exportação de outra conta.
 */
@RestController
@RequestMapping("/me/export")
@RequiredArgsConstructor
public class TaskExportController {

    private static final MediaType TEXT_CSV = new MediaType("text", "csv", StandardCharsets.UTF_8);

    private final TaskExportService taskExportService;

    @GetMapping
    public ResponseEntity<Object> export(@RequestParam(required = false) String format,
                                         @AuthenticationPrincipal UUID userId) {
        ExportFormat formato;
        try {
            formato = ExportFormat.from(format);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }

        TaskExportResponse export = taskExportService.export(userId);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentDisposition(ContentDisposition.attachment()
                .filename(taskExportService.fileName(formato, LocalDate.now()))
                .build());

        if (formato == ExportFormat.CSV) {
            return ResponseEntity.ok().headers(headers).contentType(TEXT_CSV)
                    .body(taskExportService.toCsv(export));
        }
        return ResponseEntity.ok().headers(headers).contentType(MediaType.APPLICATION_JSON)
                .body(export);
    }
}
