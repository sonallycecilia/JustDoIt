package com.justdoit.task.feature.usernote;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.UserNoteRequest;
import com.justdoit.task.shared.UserNoteResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/me/note")
@RequiredArgsConstructor
public class UserNoteController {

    private final UserNoteService noteService;
    private final JwtUtil jwtUtil;

    @GetMapping
    public ResponseEntity<UserNoteResponse> getNote(HttpServletRequest httpRequest) {
        return ResponseEntity.ok(noteService.getNote(extractUserId(httpRequest)));
    }

    @PutMapping
    public ResponseEntity<UserNoteResponse> upsertNote(@RequestBody @Valid UserNoteRequest request,
                                                       HttpServletRequest httpRequest) {
        return ResponseEntity.ok(noteService.upsertNote(extractUserId(httpRequest), request));
    }

    private UUID extractUserId(HttpServletRequest request) {
        String token = request.getHeader("Authorization").substring(7);
        return jwtUtil.extractUserId(token);
    }
}