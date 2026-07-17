package com.justdoit.task.feature.usernote;

import com.justdoit.task.shared.UserNoteRequest;
import com.justdoit.task.shared.UserNoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserNoteService {

    private final UserNoteRepository noteRepository;

    /**
     * Retorna o bloco do usuário. Se ainda não existir, devolve um bloco vazio
     * (em vez de 404) para o frontend simplesmente abrir a caixa em branco.
     */
    @Transactional(readOnly = true)
    public UserNoteResponse getNote(UUID userId) {
        return noteRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseGet(() -> new UserNoteResponse(null, "", null, null));
    }

    @Transactional
    public UserNoteResponse upsertNote(UUID userId, UserNoteRequest request) {
        UserNote note = noteRepository.findByUserId(userId)
                .orElseGet(() -> UserNote.builder().userId(userId).build());
        note.setContent(request.content());
        return toResponse(noteRepository.save(note));
    }

    private UserNoteResponse toResponse(UserNote note) {
        return new UserNoteResponse(
                note.getId(),
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}