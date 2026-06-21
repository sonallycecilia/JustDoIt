package com.justdoit.task.feature.task;

import com.justdoit.task.shared.SubTaskRequest;
import com.justdoit.task.shared.SubTaskResponse;
import com.justdoit.task.shared.TaskRequest;
import com.justdoit.task.shared.TaskResponse;
import com.justdoit.task.config.JwtUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final JwtUtil jwtUtil;

    @PostMapping
    public ResponseEntity<TaskResponse> createTask(@RequestBody @Valid TaskRequest request,
                                                   HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(taskService.createTask(request, userId));
    }

    @GetMapping
    public ResponseEntity<List<TaskResponse>> getAllTasks(HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        return ResponseEntity.ok(taskService.getAllTasksByUser(userId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskResponse> getTaskById(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(taskService.getTaskById(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<TaskResponse> updateTask(@PathVariable UUID id,
                                                   @RequestBody @Valid TaskRequest request,
                                                   HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(taskService.updateTask(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            taskService.deleteTask(id, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/complete")
    public ResponseEntity<TaskResponse> completeTask(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(taskService.completeTask(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping("/{id}/subtasks")
    public ResponseEntity<SubTaskResponse> addSubTask(@PathVariable UUID id,
                                                      @RequestBody @Valid SubTaskRequest request,
                                                      HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(taskService.addSubTask(id, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @GetMapping("/{id}/subtasks/progress")
    public ResponseEntity<Double> getSubTaskProgress(@PathVariable UUID id, HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(taskService.getSubTaskProgress(id, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}
