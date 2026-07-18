package com.justdoit.task.shared;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Resposta do bloco fixado em /me/note. Espelha o antigo UserNoteResponse para
 * o frontend não perceber a migração de UserNote para Note.
 */
public record MeNoteResponse(
    UUID id,
    String content,
    LocalDateTime createdAt,
    LocalDateTime updatedAt
) {}
