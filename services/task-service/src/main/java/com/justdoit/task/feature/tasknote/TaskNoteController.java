package com.justdoit.task.feature.tasknote;

import com.justdoit.task.shared.TaskNoteRequest;
import com.justdoit.task.shared.TaskNoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/note")
@RequiredArgsConstructor
public class TaskNoteController {

    private final TaskNoteService noteService;

    @GetMapping
    public ResponseEntity<TaskNoteResponse> getNote(@PathVariable UUID taskId,
                                                     @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.getNote(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<TaskNoteResponse> upsertNote(@PathVariable UUID taskId,
                                                        @RequestBody @Valid TaskNoteRequest request,
                                                        @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.upsertNote(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
