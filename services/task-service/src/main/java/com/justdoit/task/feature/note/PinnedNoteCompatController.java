package com.justdoit.task.feature.note;

import com.justdoit.task.shared.MeNoteRequest;
import com.justdoit.task.shared.MeNoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Compatibilidade com o frontend atual: o bloco de anotação único no topo da
 * página To Do continua em {@code GET/PUT /me/note}, mas agora opera sobre a
 * nota FIXADA da nova entidade Note. Mantém o mesmo contrato do antigo UserNote
 * (mesmos campos; GET sem nota devolve bloco vazio em vez de 404).
 */
@RestController
@RequestMapping("/me/note")
@RequiredArgsConstructor
public class PinnedNoteCompatController {

    private final NoteService noteService;

    @GetMapping
    public ResponseEntity<MeNoteResponse> getNote(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(noteService.getPinned(userId));
    }

    @PutMapping
    public ResponseEntity<MeNoteResponse> upsertNote(@RequestBody @Valid MeNoteRequest request,
                                                     @AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(noteService.upsertPinned(userId, request));
    }
}
