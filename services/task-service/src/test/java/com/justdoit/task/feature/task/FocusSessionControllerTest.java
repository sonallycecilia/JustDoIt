package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.FocusSessionRequest;
import com.justdoit.task.shared.FocusSessionResponse;
import com.justdoit.task.shared.SessionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(FocusSessionController.class)
class FocusSessionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private FocusSessionService sessionService;
    @MockitoBean private JwtUtil jwtUtil;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    @Test
    @WithMockUser
    void listSessions_returnsOk() throws Exception {
        FocusSessionResponse s1 = new FocusSessionResponse(SESSION_ID, TASK_ID, 25, 5, SessionType.FOCUS, null, null, false);
        FocusSessionResponse s2 = new FocusSessionResponse(UUID.randomUUID(), TASK_ID, 50, 10, SessionType.BREAK, null, null, true);
        when(sessionService.listSessions(TASK_ID, USER_ID)).thenReturn(List.of(s1, s2));

        mockMvc.perform(get("/tasks/{taskId}/focus-sessions", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].focusMinutes").value(25))
                .andExpect(jsonPath("$[1].completed").value(true));
    }

    @Test
    @WithMockUser
    void listSessions_whenTaskNotFound_returns404() throws Exception {
        when(sessionService.listSessions(TASK_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{taskId}/focus-sessions", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void createSession_returnsCreated() throws Exception {
        FocusSessionRequest request = new FocusSessionRequest(25, 5, SessionType.FOCUS, null, null, null);
        FocusSessionResponse response = new FocusSessionResponse(
                SESSION_ID, TASK_ID, 25, 5, SessionType.FOCUS, null, null, false);
        when(sessionService.createSession(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(post("/tasks/{taskId}/focus-sessions", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(SESSION_ID.toString()))
                .andExpect(jsonPath("$.focusMinutes").value(25))
                .andExpect(jsonPath("$.completed").value(false));
    }

    @Test
    @WithMockUser
    void completeSession_returnsOk() throws Exception {
        FocusSessionResponse response = new FocusSessionResponse(
                SESSION_ID, TASK_ID, 25, 5, SessionType.FOCUS, null, null, true);
        when(sessionService.completeSession(TASK_ID, SESSION_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(patch("/tasks/{taskId}/focus-sessions/{sessionId}/complete", TASK_ID, SESSION_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completed").value(true));
    }

    @Test
    @WithMockUser
    void deleteSession_returnsNoContent() throws Exception {
        doNothing().when(sessionService).deleteSession(TASK_ID, SESSION_ID, USER_ID);

        mockMvc.perform(delete("/tasks/{taskId}/focus-sessions/{sessionId}", TASK_ID, SESSION_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void deleteSession_whenNotFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found"))
                .when(sessionService).deleteSession(TASK_ID, SESSION_ID, USER_ID);

        mockMvc.perform(delete("/tasks/{taskId}/focus-sessions/{sessionId}", TASK_ID, SESSION_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }
}
