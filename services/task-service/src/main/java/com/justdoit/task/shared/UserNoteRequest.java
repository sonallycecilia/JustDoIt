package com.justdoit.task.shared;

import jakarta.validation.constraints.Size;

/**
 * Conteúdo do bloco de anotações do usuário. Pode vir vazio (o usuário limpou
 * o bloco), por isso não usa {@code @NotBlank}.
 */
public record UserNoteRequest(
    @Size(max = 10000) String content
) {}