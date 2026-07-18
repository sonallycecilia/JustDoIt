package com.justdoit.task.feature.note;

import com.justdoit.task.shared.MeNoteRequest;
import com.justdoit.task.shared.MeNoteResponse;
import com.justdoit.task.shared.NoteRequest;
import com.justdoit.task.shared.NoteResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NoteServiceTest {

    @Mock private NoteRepository noteRepository;
    @InjectMocks private NoteService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID OTHER_ID = UUID.fromString("00000000-0000-0000-0000-000000000009");

    private Note note(UUID id, boolean pinned, String content) {
        return Note.builder().id(id).userId(USER_ID).title("t").content(content).pinned(pinned).build();
    }

    @Test
    void list_mapsNotes() {
        when(noteRepository.findByUserIdOrderByPinnedDescUpdatedAtDesc(USER_ID))
                .thenReturn(List.of(note(NOTE_ID, true, "a")));

        List<NoteResponse> result = service.list(USER_ID);

        assertEquals(1, result.size());
        assertEquals(NOTE_ID, result.get(0).id());
        assertTrue(result.get(0).pinned());
    }

    @Test
    void create_savesUnpinned() {
        when(noteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.create(USER_ID, new NoteRequest("Título", "Corpo"));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals("Título", captor.getValue().getTitle());
        assertFalse(captor.getValue().isPinned());
    }

    @Test
    void get_notOwned_throws() {
        when(noteRepository.findByIdAndUserId(NOTE_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.get(USER_ID, NOTE_ID));
    }

    @Test
    void update_changesTitleAndContent() {
        when(noteRepository.findByIdAndUserId(NOTE_ID, USER_ID)).thenReturn(Optional.of(note(NOTE_ID, false, "old")));
        when(noteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NoteResponse result = service.update(USER_ID, NOTE_ID, new NoteRequest("novo", "conteúdo"));

        assertEquals("novo", result.title());
        assertEquals("conteúdo", result.content());
    }

    @Test
    void pin_unpinsPreviousAndPinsThis() {
        Note previous = note(OTHER_ID, true, "antiga");
        Note target = note(NOTE_ID, false, "nova");
        when(noteRepository.findByIdAndUserId(NOTE_ID, USER_ID)).thenReturn(Optional.of(target));
        when(noteRepository.findByUserIdAndPinnedTrue(USER_ID)).thenReturn(Optional.of(previous));
        when(noteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        NoteResponse result = service.pin(USER_ID, NOTE_ID);

        assertFalse(previous.isPinned(), "a nota fixada anterior deve ser desafixada");
        assertTrue(result.pinned());
        verify(noteRepository).save(previous);
        verify(noteRepository).save(target);
    }

    @Test
    void pin_alreadyPinned_isNoop() {
        Note target = note(NOTE_ID, true, "já fixada");
        when(noteRepository.findByIdAndUserId(NOTE_ID, USER_ID)).thenReturn(Optional.of(target));

        service.pin(USER_ID, NOTE_ID);

        verify(noteRepository, never()).findByUserIdAndPinnedTrue(any());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void getPinned_whenAbsent_returnsEmptyBlock() {
        when(noteRepository.findByUserIdAndPinnedTrue(USER_ID)).thenReturn(Optional.empty());

        MeNoteResponse result = service.getPinned(USER_ID);

        assertNull(result.id());
        assertEquals("", result.content());
    }

    @Test
    void upsertPinned_whenAbsent_createsPinned() {
        when(noteRepository.findByUserIdAndPinnedTrue(USER_ID)).thenReturn(Optional.empty());
        when(noteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertPinned(USER_ID, new MeNoteRequest("bloco novo"));

        ArgumentCaptor<Note> captor = ArgumentCaptor.forClass(Note.class);
        verify(noteRepository).save(captor.capture());
        assertTrue(captor.getValue().isPinned());
        assertEquals("bloco novo", captor.getValue().getContent());
    }

    @Test
    void upsertPinned_whenPresent_updatesContent() {
        Note existing = note(NOTE_ID, true, "antigo");
        when(noteRepository.findByUserIdAndPinnedTrue(USER_ID)).thenReturn(Optional.of(existing));
        when(noteRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        MeNoteResponse result = service.upsertPinned(USER_ID, new MeNoteRequest("atualizado"));

        assertEquals("atualizado", result.content());
        assertEquals(NOTE_ID, result.id());
    }
}
