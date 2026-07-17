package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import com.justdoit.task.shared.TaskNoteRequest;
import com.justdoit.task.shared.TaskNoteResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskNoteController.class)
class TaskNoteControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TaskNoteService noteService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
    }

    @Test
    void getNote_returnsOk() throws Exception {
        TaskNoteResponse response = new TaskNoteResponse(
                NOTE_ID, TASK_ID, "My note content", LocalDateTime.now(), LocalDateTime.now());
        when(noteService.getNote(TASK_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/tasks/{taskId}/note", TASK_ID)
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(NOTE_ID.toString()))
                .andExpect(jsonPath("$.content").value("My note content"));
    }

    @Test
    void getNote_whenNotFound_returns404() throws Exception {
        when(noteService.getNote(TASK_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{taskId}/note", TASK_ID)
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void upsertNote_returnsOk() throws Exception {
        TaskNoteRequest request = new TaskNoteRequest("Updated content");
        TaskNoteResponse response = new TaskNoteResponse(
                NOTE_ID, TASK_ID, "Updated content", LocalDateTime.now(), LocalDateTime.now());
        when(noteService.upsertNote(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(put("/tasks/{taskId}/note", TASK_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("Updated content"));
    }

    @Test
    void upsertNote_withBlankContent_returnsBadRequest() throws Exception {
        mockMvc.perform(put("/tasks/{taskId}/note", TASK_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskNoteRequest(""))))
                .andExpect(status().isBadRequest());
    }
}
