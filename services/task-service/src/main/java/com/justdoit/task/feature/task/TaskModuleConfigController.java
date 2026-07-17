package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskModuleConfigRequest;
import com.justdoit.task.shared.TaskModuleConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/module-config")
@RequiredArgsConstructor
public class TaskModuleConfigController {

    private final TaskModuleConfigService configService;

    @GetMapping
    public ResponseEntity<TaskModuleConfigResponse> getConfig(@PathVariable UUID taskId,
                                                               @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(configService.getConfig(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<TaskModuleConfigResponse> upsertConfig(@PathVariable UUID taskId,
                                                                  @RequestBody TaskModuleConfigRequest request,
                                                                  @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(configService.upsertConfig(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
