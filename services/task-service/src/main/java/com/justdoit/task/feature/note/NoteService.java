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

/**
 * service aplica as regras de negócio, valida permissões de usuário e orquestra as chamadas ao banco de dados.
 */
@Service 
@RequiredArgsConstructor
public class NoteService {

    private final NoteRepository noteRepository;

    // ---- Aba "Anotações": CRUD de notas do usuário ----

    @Transactional(readOnly = true)
    public List<NoteResponse> list(UUID userId) {
        // ordena colocando as fixadas (Pinned) no topo (Desc)
        // depois ordena pela data de atualização mais recente (UpdatedAtDesc).
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
                .pinned(false) // Toda nota nova nasce sem estar fixada.
                .build();
        return toResponse(noteRepository.saveAndFlush(note));
    }

    @Transactional(readOnly = true)
    public NoteResponse get(UUID userId, UUID noteId) {
        // o metodo findOwned() garante que o usuário só consiga buscar notas que ele é dono.
        return toResponse(findOwned(userId, noteId));
    }

    @Transactional
    public NoteResponse update(UUID userId, UUID noteId, NoteRequest request) {
        Note note = findOwned(userId, noteId);
        note.setTitle(request.title());
        note.setContent(request.content());
        return toResponse(noteRepository.saveAndFlush(note));
    }

    @Transactional
    public void delete(UUID userId, UUID noteId) {
        noteRepository.delete(findOwned(userId, noteId));
    }

    /**
     * Aqui mora a regra de negócio do PATCH /pin
     */
    @Transactional
    public NoteResponse pin(UUID userId, UUID noteId) {
        Note note = findOwned(userId, noteId); // Pega a nota que o usuário quer fixar.
        
        if (!note.isPinned()) {
            // Se ela já não estiver fixada, precisamos achar quem está fixada atualmente e desafixá-la. 
            // Só pode existir UMA nota fixada por usuário.
            noteRepository.findByUserIdAndPinnedTrue(userId).ifPresent(prev -> {
                prev.setPinned(false); // Descoroa a nota antiga.
                noteRepository.save(prev);
            });
            // Coroa a nova nota.
            note.setPinned(true);
            noteRepository.saveAndFlush(note);
        }
        return toResponse(note);
    }

    // ---- Compatibilidade /me/note ----
    
    // dashboard do usuário, que mostra a nota fixada (pinned) ou uma nota vazia se não houver nenhuma fixada.

    @Transactional(readOnly = true)
    public MeNoteResponse getPinned(UUID userId) {
        return noteRepository.findByUserIdAndPinnedTrue(userId)
                .map(n -> new MeNoteResponse(n.getId(), n.getContent(), n.getCreatedAt(), n.getUpdatedAt()))
                // Se o usuário não tiver nota fixada, não devolvemos erro 404. 
                // Cumprimos o contrato do frontend devolvendo uma resposta com conteúdo vazio "".
                .orElseGet(() -> new MeNoteResponse(null, "", null, null));
    }

    @Transactional
    public MeNoteResponse upsertPinned(UUID userId, MeNoteRequest request) {
        // Tenta achar a nota fixada. Se não achar, cria uma "fantasma" já com pinned=true.
        Note note = noteRepository.findByUserIdAndPinnedTrue(userId)
                .orElseGet(() -> Note.builder().userId(userId).pinned(true).build());
        
        note.setContent(request.content());
        Note saved = noteRepository.saveAndFlush(note);
        
        return new MeNoteResponse(saved.getId(), saved.getContent(), saved.getCreatedAt(), saved.getUpdatedAt());
    }

    // -------------------------------------------------------------------------
    // MÉTODOS AUXILIARES (Private)
    // -------------------------------------------------------------------------

    /**
     * procura pela nota EXIGINDO que o userId seja o dono
     *  se forjarem tentando passar o ID da nota de outro usuário, o banco
     * não acha e dispara o erro, bloqueando a ação.
     */
    private Note findOwned(UUID userId, UUID noteId) {
        return noteRepository.findByIdAndUserId(noteId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
    }

    /**
     * Mapper manual: Converte a Entidade de Banco (Note) para o DTO (NoteResponse).
     * Evita que o frontend receba dados sensíveis ou irrelevantes que possam existir na tabela.
     */
    private NoteResponse toResponse(Note n) {
        return new NoteResponse(n.getId(), n.getTitle(), n.getContent(), n.isPinned(),
                n.getCreatedAt(), n.getUpdatedAt());
    }
}