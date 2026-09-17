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

/**
 * estoque de endpoints que serão consumidos pelo frontend para gerenciar as anotações livres do usuário.
 */
@RestController
@RequestMapping("/notes") 
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * (GET/notes) - Listar todas as anotações do usuário logado.
     */
    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(@AuthenticationPrincipal UUID userId) {
        // Devolve a lista inteira
        return ResponseEntity.ok(noteService.list(userId));
    }

    /**
     * (POST /notes) - criação de uma nova anotação
     */
    @PostMapping
    public ResponseEntity<NoteResponse> create(
            @RequestBody @Valid NoteRequest request,
            @AuthenticationPrincipal UUID userId) {
        // retorna HTTP 201 (CREATED)
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(userId, request));
    }

    /**
     * (GET /notes/{id}) - busca uma anotação específica por id
     */
    @GetMapping("/{id}")
    public ResponseEntity<NoteResponse> get(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.get(userId, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * (PUT /notes/{id}) - atualizar uma anotação existente
     * pega o corpo inteiro que o frontend mandou e substitui os dados da nota que tem esse {id}.
     */
    @PutMapping("/{id}")
    public ResponseEntity<NoteResponse> update(
            @PathVariable UUID id,
            @RequestBody @Valid NoteRequest request,
            @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.update(userId, id, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * (DELETE /notes/{id}) - Deletar a anotação
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            noteService.delete(userId, id);
            return ResponseEntity.noContent().build(); // HTTP 204: Deletado com sucesso.
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * (PATCH /notes/{id}/pin) - Fixar ou Desafixar a anotação
     */
    @PatchMapping("/{id}/pin")
    public ResponseEntity<NoteResponse> pin(@PathVariable UUID id, @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(noteService.pin(userId, id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}