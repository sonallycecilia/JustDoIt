package com.justdoit.task.feature.task;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.TaskModuleConfigRequest;
import com.justdoit.task.shared.TaskModuleConfigResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/module-config")
@RequiredArgsConstructor
public class TaskModuleConfigController {

    private final TaskModuleConfigService configService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<TaskModuleConfigResponse> getConfig(@PathVariable UUID taskId,
                                                               HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(configService.getConfig(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<TaskModuleConfigResponse> upsertConfig(@PathVariable UUID taskId,
                                                                  @RequestBody TaskModuleConfigRequest request,
                                                                  HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(configService.upsertConfig(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}
