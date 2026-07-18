package com.justdoit.task.feature.report;

import com.justdoit.task.shared.TaskReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Relatório agregado do usuário por período. Consumidor principal: o
 * schedule-service (resumo semanal), que repassa o token do próprio usuário —
 * por isso o endpoint é autenticado como qualquer outro, sem credencial especial.
 */
@RestController
@RequiredArgsConstructor
public class TaskReportController {

    private final TaskReportService taskReportService;

    @GetMapping("/tasks/report")
    public ResponseEntity<TaskReportResponse> getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskReportService.getReport(userId, from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
