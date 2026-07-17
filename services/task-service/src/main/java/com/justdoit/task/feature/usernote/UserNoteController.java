package com.justdoit.task.feature.usernote;

import com.justdoit.task.shared.UserNoteRequest;
import com.justdoit.task.shared.UserNoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/me/note")
@RequiredArgsConstructor
public class UserNoteController {

    private final UserNoteService noteService;

    @GetMapping
    public ResponseEntity<UserNoteResponse> getNote(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(noteService.getNote(userId));
    }

    @PutMapping
    public ResponseEntity<UserNoteResponse> upsertNote(@RequestBody @Valid UserNoteRequest request,
                                                       @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(noteService.upsertNote(userId, request));
    }
}
