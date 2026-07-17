package com.justdoit.task.feature.task;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.CycleConfigRequest;
import com.justdoit.task.shared.CycleConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/cycle-config")
@RequiredArgsConstructor
public class CycleConfigController {

    private final CycleConfigService cycleConfigService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<CycleConfigResponse> getCycleConfig(@PathVariable UUID taskId,
                                                               HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(cycleConfigService.getCycleConfig(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<CycleConfigResponse> upsertCycleConfig(@PathVariable UUID taskId,
                                                                   @RequestBody @Valid CycleConfigRequest request,
                                                                   HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(cycleConfigService.upsertCycleConfig(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteCycleConfig(@PathVariable UUID taskId, HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            cycleConfigService.deleteCycleConfig(taskId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}
