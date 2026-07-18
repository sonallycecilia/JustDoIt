package com.justdoit.task.feature.note;

import com.justdoit.task.shared.NoteRequest;
import com.justdoit.task.shared.NoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/notes")
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(noteService.list(userId));
    }

    @PostMapping
    public ResponseEntity<NoteResponse> create(@RequestBody @Valid NoteRequest request,
                                               @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(userId, request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> get(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.get(userId, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> update(@PathVariable UUID id,
                                               @RequestBody @Valid NoteRequest request,
                                               @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.update(userId, id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            noteService.delete(userId, id);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/{id}/pin")
    public ResponseEntity<NoteResponse> pin(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.pin(userId, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
