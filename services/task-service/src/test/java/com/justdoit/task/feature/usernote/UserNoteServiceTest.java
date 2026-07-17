package com.justdoit.task.feature.usernote;

import com.justdoit.task.shared.UserNoteRequest;
import com.justdoit.task.shared.UserNoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserNoteServiceTest {

    @Mock private UserNoteRepository noteRepository;
    @InjectMocks private UserNoteService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private UserNote note;

    @BeforeEach
    void setUp() {
        note = UserNote.builder().id(NOTE_ID).userId(USER_ID).content("Original note")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void getNote_returnsResponse() {
        when(noteRepository.findByUserId(USER_ID)).thenReturn(Optional.of(note));

        UserNoteResponse result = service.getNote(USER_ID);

        assertEquals(NOTE_ID, result.id());
        assertEquals("Original note", result.content());
        assertNotNull(result.createdAt());
    }

    @Test
    void getNote_whenAbsent_returnsEmptyBlock() {
        when(noteRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        UserNoteResponse result = service.getNote(USER_ID);

        assertNull(result.id());
        assertEquals("", result.content());
        verify(noteRepository, never()).save(any());
    }

    @Test
    void upsertNote_whenAbsent_createsNew() {
        UserNoteRequest request = new UserNoteRequest("New content");
        UserNote saved = UserNote.builder().id(NOTE_ID).userId(USER_ID).content("New content").build();
        when(noteRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(noteRepository.save(any())).thenReturn(saved);

        UserNoteResponse result = service.upsertNote(USER_ID, request);

        ArgumentCaptor<UserNote> captor = ArgumentCaptor.forClass(UserNote.class);
        verify(noteRepository).save(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals("New content", captor.getValue().getContent());
        assertEquals("New content", result.content());
    }

    @Test
    void upsertNote_whenPresent_updatesContent() {
        UserNoteRequest request = new UserNoteRequest("Updated content");
        UserNote saved = UserNote.builder().id(NOTE_ID).userId(USER_ID).content("Updated content").build();
        when(noteRepository.findByUserId(USER_ID)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenReturn(saved);

        UserNoteResponse result = service.upsertNote(USER_ID, request);

        ArgumentCaptor<UserNote> captor = ArgumentCaptor.forClass(UserNote.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("Updated content", captor.getValue().getContent());
        assertEquals("Updated content", result.content());
    }
}
