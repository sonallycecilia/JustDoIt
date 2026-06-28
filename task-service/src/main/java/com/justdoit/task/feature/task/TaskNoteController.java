package com.justdoit.task.feature.task;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.TaskNoteRequest;
import com.justdoit.task.shared.TaskNoteResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/note")
@RequiredArgsConstructor
public class TaskNoteController {

    private final TaskNoteService noteService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<TaskNoteResponse> getNote(@PathVariable UUID taskId,
                                                     HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(noteService.getNote(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<TaskNoteResponse> upsertNote(@PathVariable UUID taskId,
                                                        @RequestBody @Valid TaskNoteRequest request,
                                                        HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(noteService.upsertNote(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}
