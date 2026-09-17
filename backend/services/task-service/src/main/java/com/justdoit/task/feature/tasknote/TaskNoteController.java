package com.justdoit.task.feature.tasknote;

import com.justdoit.task.shared.TaskNoteRequest;
import com.justdoit.task.shared.TaskNoteResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * expõe os endpoints para o frontend 
 * q pode visualizar, criar, editar e excluir a anotação de uma tarefa.
 */

// --- Anotações do Spring Boot e Lombok ---

@RestController 

// Define a "rota base" para todos os métodos desta classe
@RequestMapping("/tasks/{taskId}/note")  //indica que toda anotação pertence a uma tarefa específica (taskId)


@RequiredArgsConstructor 
public class TaskNoteController {

    // controller sempre delega as regras de negócio para o Service, que é a camada responsável por isso.
    private final TaskNoteService noteService;


    /**
     * ENDPOINT: Buscar uma anotação (GET)
     */
    @GetMapping 
    public ResponseEntity<TaskNoteResponse> getNote(
            @PathVariable UUID taskId, 
            
            // Pega o ID do usuário que está logado direto do token
            // Impede que o usuário A busque a nota do usuário B passando apenas o ID na URL.
            @AuthenticationPrincipal UUID userId) {
        try {
            // Se der tudo certo, retorna http 200 com a nota no payload da resoosta
            return ResponseEntity.ok(noteService.getNote(taskId, userId));
        } catch (IllegalArgumentException e) {
            // Se o Service não achar a nota lança http 404 (not found)
            return ResponseEntity.notFound().build();
        }
    }
    // obs: como a relação pe 1 p 1, não faz sentido ter um POST 
    // para criar uma nota, pois ela sempre será criada junto com a tarefa.

    /**
     * ENDPOINT: Criar ou Atualizar uma anotação (PUT)
     */
    @PutMapping 
    public ResponseEntity<TaskNoteResponse> upsertNote(
            @PathVariable UUID taskId,
            
            // @RequestBody: pega o JSON que o frontend enviou e converte para a classe TaskNoteRequest.
            @RequestBody @Valid TaskNoteRequest request,
            
            @AuthenticationPrincipal UUID userId) {
        try {
            // Repassa a requisição, ID da tarefa e ID do usuário para o Service salvar no banco.
            return ResponseEntity.ok(noteService.upsertNote(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            // traduz um erro 404 (not found) do Service para o mesmo erro na resposta HTTP.
            return ResponseEntity.notFound().build();
        }
    }


    /**
     * ENDPOINT: Deletar uma anotação (DELETE)
     */
    @DeleteMapping 
    public ResponseEntity<Void> deleteNote(
            @PathVariable UUID taskId,
            @AuthenticationPrincipal UUID userId) {
        try {
            // Manda o Service apagar a nota no banco de dados.
            noteService.deleteNote(taskId, userId);
            
            // Retorna HTTP 204 (No Content)
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}