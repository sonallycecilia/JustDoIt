package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.TaskTimerLogRequest;
import com.justdoit.task.shared.TaskTimerRequest;
import com.justdoit.task.shared.TaskTimerResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskTimerController.class)
class TaskTimerControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private TaskTimerService timerService;
    @MockBean private JwtUtil jwtUtil;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TIMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    @Test
    @WithMockUser
    void getTimer_returnsOk() throws Exception {
        TaskTimerResponse response = new TaskTimerResponse(TIMER_ID, TASK_ID, 30, 0L, null);
        when(timerService.getTimer(TASK_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/tasks/{taskId}/timer", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TIMER_ID.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(30))
                .andExpect(jsonPath("$.actualSeconds").value(0));
    }

    @Test
    @WithMockUser
    void getTimer_whenNotFound_returns404() throws Exception {
        when(timerService.getTimer(TASK_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{taskId}/timer", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void upsertTimer_returnsOk() throws Exception {
        TaskTimerRequest request = new TaskTimerRequest(45, null, null);
        TaskTimerResponse response = new TaskTimerResponse(TIMER_ID, TASK_ID, 45, 0L, null);
        when(timerService.upsertTimer(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(put("/tasks/{taskId}/timer", TASK_ID)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estimatedMinutes").value(45));
    }

    @Test
    @WithMockUser
    void logSeconds_returnsOk() throws Exception {
        TaskTimerLogRequest request = new TaskTimerLogRequest(300L);
        TaskTimerResponse response = new TaskTimerResponse(TIMER_ID, TASK_ID, 30, 300L, null);
        when(timerService.logSeconds(eq(TASK_ID), eq(300L), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(patch("/tasks/{taskId}/timer/log", TASK_ID)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.actualSeconds").value(300));
    }

    @Test
    @WithMockUser
    void logSeconds_whenTimerNotFound_returns404() throws Exception {
        when(timerService.logSeconds(eq(TASK_ID), anyLong(), eq(USER_ID)))
                .thenThrow(new IllegalArgumentException("timer not found"));

        mockMvc.perform(patch("/tasks/{taskId}/timer/log", TASK_ID)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskTimerLogRequest(60L))))
                .andExpect(status().isNotFound());
    }
}
