package com.justdoit.task.feature.task;

import com.justdoit.task.shared.SubTaskRequest;
import com.justdoit.task.shared.SubTaskResponse;
import com.justdoit.task.shared.TaskRequest;
import com.justdoit.task.shared.TaskResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@RequestBody @Valid TaskRequest request,
                                                   @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(taskService.getAllTasksByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskService.getTaskById(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID id,
                                                   @RequestBody @Valid TaskRequest request,
                                                   @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskService.updateTask(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            taskService.deleteTask(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(@PathVariable UUID id,
                                                     @AuthenticationPrincipal UUID userId,
                                                     @RequestHeader(HttpHeaders.AUTHORIZATION) String authHeader) {
        try {
            // O header segue junto para o notification-service ser chamado com o
            // token do próprio usuário (nunca com credencial do serviço).
            return ResponseEntity.ok(taskService.completeTask(id, userId, authHeader));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/reopen")
    public ResponseEntity<TaskResponse> reopenTask(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskService.reopenTask(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/subtasks")
    public ResponseEntity<SubTaskResponse> addSubTask(@PathVariable UUID id,
                                                      @RequestBody @Valid SubTaskRequest request,
                                                      @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(taskService.addSubTask(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/subtasks")
    public ResponseEntity<List<SubTaskResponse>> listSubTasks(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskService.getSubTasks(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/subtasks/{subId}/toggle")
    public ResponseEntity<SubTaskResponse> toggleSubTask(@PathVariable UUID id,
                                                         @PathVariable UUID subId,
                                                         @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskService.toggleSubTask(id, subId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}/subtasks/{subId}")
    public ResponseEntity<Void> deleteSubTask(@PathVariable UUID id,
                                              @PathVariable UUID subId,
                                              @AuthenticationPrincipal UUID userId) {
        try {
            taskService.deleteSubTask(id, subId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/subtasks/progress")
    public ResponseEntity<Double> getSubTaskProgress(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(taskService.getSubTaskProgress(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
