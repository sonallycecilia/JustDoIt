package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskTimerLogRequest;
import com.justdoit.task.shared.TaskTimerRequest;
import com.justdoit.task.shared.TaskTimerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/timer")
@RequiredArgsConstructor
public class TaskTimerController {

    private final TaskTimerService timerService;

    @GetMapping
    public ResponseEntity<TaskTimerResponse> getTimer(@PathVariable UUID taskId,
                                                       @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.getTimer(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<TaskTimerResponse> upsertTimer(@PathVariable UUID taskId,
                                                          @RequestBody TaskTimerRequest request,
                                                          @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.upsertTimer(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/log")
    public ResponseEntity<TaskTimerResponse> logSeconds(@PathVariable UUID taskId,
                                                         @RequestBody @Valid TaskTimerLogRequest request,
                                                         @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.logSeconds(taskId, request.seconds(), userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
