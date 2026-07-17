package com.justdoit.task.feature.task;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.TaskReportResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
    private final JwtUtil jwtUtil;

    @GetMapping("/tasks/report")
    public ResponseEntity<TaskReportResponse> getReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(taskReportService.getReport(userId, from, to));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}
