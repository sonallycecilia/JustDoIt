package com.justdoit.task.feature.task;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.FocusSessionRequest;
import com.justdoit.task.shared.FocusSessionResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/focus-sessions")
@RequiredArgsConstructor
public class FocusSessionController {

    private final FocusSessionService sessionService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<List<FocusSessionResponse>> listSessions(@PathVariable UUID taskId,
                                                                    HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(sessionService.listSessions(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping
    public ResponseEntity<FocusSessionResponse> createSession(@PathVariable UUID taskId,
                                                               @RequestBody FocusSessionRequest request,
                                                               HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.status(HttpStatus.CREATED).body(sessionService.createSession(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{sessionId}/complete")
    public ResponseEntity<FocusSessionResponse> completeSession(@PathVariable UUID taskId,
                                                                 @PathVariable UUID sessionId,
                                                                 HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            return ResponseEntity.ok(sessionService.completeSession(taskId, sessionId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable UUID taskId,
                                               @PathVariable UUID sessionId,
                                               HttpServletRequest httpRequest) {
        UUID userId = extractUserId(httpRequest);
        try {
            sessionService.deleteSession(taskId, sessionId, userId);
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
