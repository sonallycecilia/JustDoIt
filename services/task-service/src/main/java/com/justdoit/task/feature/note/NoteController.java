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
 * Controller REST para as anotações livres do usuário.
 * Mapeia os endpoints que vão alimentar a aba "Anotações" na Sidebar.
 */
@RestController
@RequestMapping("/notes") // O endereço agora é genérico e direto (não tem /tasks/ no meio)
@RequiredArgsConstructor
public class NoteController {

    private final NoteService noteService;

    /**
     * ENDPOINT: Listar TODAS as anotações (GET /notes)
     * Atende ao Critério de Aceitação 1 do card: "exibir uma lista de todas as anotações".
     */
    @GetMapping
    public ResponseEntity<List<NoteResponse>> list(@AuthenticationPrincipal UUID userId) {
        // Devolve a lista inteira. O Service provavelmente já traz isso ordenado (ex: fixadas primeiro).
        return ResponseEntity.ok(noteService.list(userId));
    }

    /**
     * ENDPOINT: Criar nova anotação (POST /notes)
     * POR QUE TEM POST AQUI? Porque "Notes" é uma coleção infinita. O frontend manda
     * o conteúdo, mas não sabe o ID. O servidor cria a nota, gera o UUID e devolve.
     */
    @PostMapping
    public ResponseEntity<NoteResponse> create(
            @RequestBody @Valid NoteRequest request,
            @AuthenticationPrincipal UUID userId) {
        // HTTP 201 (CREATED): É a melhor prática REST para quando um POST tem sucesso. 
        // Significa "Recebi seu pedido e um novo recurso foi criado no banco".
        return ResponseEntity.status(HttpStatus.CREATED).body(noteService.create(userId, request));
    }

    /**
     * ENDPOINT: Buscar uma anotação específica (GET /notes/{id})
     * Usado quando o usuário clica em uma anotação na lista da sidebar para abrir no editor.
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
     * ENDPOINT: Atualizar uma anotação existente (PUT /notes/{id})
     * O PUT aqui tem o papel clássico de "Substituir". Ele pega o corpo inteiro que o 
     * frontend mandou e substitui os dados da nota que tem esse {id}.
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
     * ENDPOINT: Deletar a anotação (DELETE /notes/{id})
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
     * ENDPOINT: Fixar ou Desafixar a anotação (PATCH /notes/{id}/pin)
     * O uso do PATCH aqui é uma aula de Clean Code e REST!
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