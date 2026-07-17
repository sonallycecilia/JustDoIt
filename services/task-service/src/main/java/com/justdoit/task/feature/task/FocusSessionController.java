package com.justdoit.task.feature.task;

import com.justdoit.task.shared.FocusSessionRequest;
import com.justdoit.task.shared.FocusSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/focus-sessions")
@RequiredArgsConstructor
public class FocusSessionController {

    private final FocusSessionService sessionService;

    @GetMapping
    public ResponseEntity<List<FocusSessionResponse>> listSessions(@PathVariable UUID taskId,
                                                                    @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(sessionService.listSessions(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<FocusSessionResponse> createSession(@PathVariable UUID taskId,
                                                               @RequestBody FocusSessionRequest request,
                                                               @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{sessionId}/complete")
    public ResponseEntity<FocusSessionResponse> completeSession(@PathVariable UUID taskId,
                                                                 @PathVariable UUID sessionId,
                                                                 @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(sessionService.completeSession(taskId, sessionId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID taskId,
                                               @PathVariable UUID sessionId,
                                               @AuthenticationPrincipal UUID userId) {
        try {
            sessionService.deleteSession(taskId, sessionId, userId);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
