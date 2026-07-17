package com.justdoit.task.feature.note;

import com.justdoit.task.shared.MeNoteRequest;
import com.justdoit.task.shared.MeNoteResponse;
import com.justdoit.task.shared.NoteRequest;
import com.justdoit.task.shared.NoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    // ---- Aba "Anotações": CRUD de notas do usuário ----

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID userId) {
        return noteRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public NoteResponse create(UUID userId, NoteRequest request) {
        Note note = Note.builder()
                .userId(userId)
                .title(request.title())
                .content(request.content())
                .pinned(false)
                .build();
        return toResponse(noteRepository.save(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse get(UUID userId, UUID noteId) {
        return toResponse(findOwned(userId, noteId));
    }

    @Transactional
    public NoteResponse update(UUID userId, UUID noteId, NoteRequest request) {
        Note note = findOwned(userId, noteId);
        note.setTitle(request.title());
        note.setContent(request.content());
        return toResponse(noteRepository.save(note));
    }

    @Transactional
    public void delete(UUID userId, UUID noteId) {
        noteRepository.delete(findOwned(userId, noteId));
    }

    /**
     * Fixa a nota no topo do To Do. Como só pode haver uma fixada por usuário,
     * despina a anterior na mesma transação antes de fixar esta.
     */
    @Transactional
    public NoteResponse pin(UUID userId, UUID noteId) {
        Note note = findOwned(userId, noteId);
        if (!note.isPinned()) {
            noteRepository.findByUserIdAndPinnedTrue(userId).ifPresent(prev -> {
                prev.setPinned(false);
                noteRepository.save(prev);
            });
            note.setPinned(true);
            noteRepository.save(note);
        }
        return toResponse(note);
    }

    // ---- Compatibilidade /me/note: a nota fixada como bloco único do To Do ----

    /** Devolve um bloco vazio (não 404) quando não há nota fixada — contrato do frontend. */
    @Transactional(readOnly = true)
    public MeNoteResponse getPinned(UUID userId) {
        return noteRepository.findByUserIdAndPinnedTrue(userId)
                .map(n -> new MeNoteResponse(n.getId(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt()))
                .orElseGet(() -> new MeNoteResponse(null, "", null, null));
    }

    @Transactional
    public MeNoteResponse upsertPinned(UUID userId, MeNoteRequest request) {
        Note note = noteRepository.findByUserIdAndPinnedTrue(userId)
                .orElseGet(() -> Note.builder().userId(userId).pinned(true).build());
        note.setContent(request.content());
        Note saved = noteRepository.save(note);
        return new MeNoteResponse(saved.getId(), saved.getContent(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    private Note findOwned(UUID userId, UUID noteId) {
        return noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
    }

    private NoteResponse toResponse(Note n) {
        return new NoteResponse(n.getId(), n.getTitle(), n.getContent(), n.isPinned(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}
