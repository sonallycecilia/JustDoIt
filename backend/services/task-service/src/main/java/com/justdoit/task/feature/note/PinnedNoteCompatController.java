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
 * Controller de Compatibilidade 
 * 
 * O objetivo desta classe é manter o frontend antigo funcionando sem quebrar.
 * Antigamente, o usuário só tinha UMA nota genérica no sistema, acessada pela URL /me/note.
 * Agora que o sistema suporta VÁRIAS notas (gerenciadas pelo NoteController na URL /notes),
 * este Controller redireciona as chamadas antigas do frontend para agir especificamente 
 * sobre a nota "Fixada" (Pinned) do novo sistema.
 */
@RestController
@RequestMapping("/me/note") 
@RequiredArgsConstructor
public class PinnedNoteCompatController {

    private final NoteService noteService;

    /**
     * ENDPOINT: Buscar o bloco de nota único (GET /me/note)
     */
    @GetMapping
    public ResponseEntity<MeNoteResponse> getNote(@AuthenticationPrincipal UUID userId) {
        
        // Chama o método getPinned do Service. 
        // O GRANDE TRUQUE AQUI: Se o usuário nunca tiver fixado uma nota, o Service NÃO 
        // lança um erro 404 (Not Found). 
        // apenas devolve um MeNoteResponse com o texto vazio ("")
        return ResponseEntity.ok(noteService.getPinned(userId));
    }

    /**
     * ENDPOINT: Salvar o bloco de nota único (PUT /me/note)
     */
    @PutMapping
    public ResponseEntity<MeNoteResponse> upsertNote(
            // Pega o conteúdo digitado no frontend
            @RequestBody @Valid MeNoteRequest request, 
            
            // Pega quem é o usuário logado
            @AuthenticationPrincipal UUID userId) {
        
        // Repassa para a lógica de "Upsert Pinned" no Service.
        // O Service vai olhar o banco de dados:
        // - Se já existe uma nota fixada, ele apenas atualiza o texto dela.
        // - Se não existe nenhuma, ele cria uma nota "fantasma" já com a flag pinned=true.
        return ResponseEntity.ok(noteService.upsertPinned(userId, request));
    }
}