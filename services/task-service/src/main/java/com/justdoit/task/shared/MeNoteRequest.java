package com.justdoit.task.shared;

import jakarta.validation.constraints.Size;

/**
 * Conteúdo do bloco de anotação fixado no topo do To Do (endpoint /me/note).
 * Pode vir vazio (o usuário limpou o bloco), por isso não usa {@code @NotBlank}.
 * Mantém o mesmo contrato do antigo UserNoteRequest.
 */
public record MeNoteRequest(
    @Size(max = 10000) String content
) {}
