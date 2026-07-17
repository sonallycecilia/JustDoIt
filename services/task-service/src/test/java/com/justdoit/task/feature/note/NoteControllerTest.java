package com.justdoit.task.feature.note;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import com.justdoit.task.shared.MeNoteResponse;
import com.justdoit.task.shared.NoteRequest;
import com.justdoit.task.shared.NoteResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest({NoteController.class, PinnedNoteCompatController.class})
class NoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private NoteService noteService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private NoteResponse sample() {
        return new NoteResponse(NOTE_ID, "Título", "Corpo", true, LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    void list_returnsOk() throws Exception {
        when(noteService.list(USER_ID)).thenReturn(List.of(sample()));

        mockMvc.perform(get("/notes").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$[0].pinned").value(true));
    }

    @Test
    void create_returnsCreated() throws Exception {
        when(noteService.create(eq(USER_ID), any())).thenReturn(sample());

        mockMvc.perform(post("/notes")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NoteRequest("Título", "Corpo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Título"));
    }

    @Test
    void create_withTooLongTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/notes")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new NoteRequest("a".repeat(256), "ok"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void get_notFound_returns404() throws Exception {
        when(noteService.get(USER_ID, NOTE_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/notes/{id}", NOTE_ID).with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(noteService).delete(USER_ID, NOTE_ID);

        mockMvc.perform(delete("/notes/{id}", NOTE_ID).with(csrf()).with(authenticatedUser(USER_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    void pin_returnsOk() throws Exception {
        when(noteService.pin(USER_ID, NOTE_ID)).thenReturn(sample());

        mockMvc.perform(patch("/notes/{id}/pin", NOTE_ID).with(csrf()).with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinned").value(true));
    }

    // ---- compat /me/note ----

    @Test
    void getMeNote_returnsPinnedBlock() throws Exception {
        when(noteService.getPinned(USER_ID))
                .thenReturn(new MeNoteResponse(NOTE_ID, "bloco", LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(get("/me/note").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.content").value("bloco"));
    }

    @Test
    void getMeNote_whenEmpty_returnsEmptyBlockNot404() throws Exception {
        when(noteService.getPinned(USER_ID)).thenReturn(new MeNoteResponse(null, "", null, null));

        mockMvc.perform(get("/me/note").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value(""));
    }

    @Test
    void putMeNote_returnsOk() throws Exception {
        when(noteService.upsertPinned(eq(USER_ID), any()))
                .thenReturn(new MeNoteResponse(NOTE_ID, "novo", LocalDateTime.now(), LocalDateTime.now()));

        mockMvc.perform(put("/me/note")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"novo\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("novo"));
    }
}
