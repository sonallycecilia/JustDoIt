package com.justdoit.task.shared;

import jakarta.validation.constraints.Size;

/**
 * Criação/edição de uma nota da aba "Anotações". Título e conteúdo são
 * opcionais (uma nota pode ser só título ou só corpo).
 */
public record NoteRequest(
    @Size(max = 255) String title,
    @Size(max = 10000) String content
) {}
