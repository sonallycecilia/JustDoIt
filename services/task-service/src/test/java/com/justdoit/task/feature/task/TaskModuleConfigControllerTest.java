package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.TaskModuleConfigRequest;
import com.justdoit.task.shared.TaskModuleConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskModuleConfigController.class)
class TaskModuleConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TaskModuleConfigService configService;
    @MockitoBean private JwtUtil jwtUtil;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    @Test
    @WithMockUser
    void getConfig_returnsOk() throws Exception {
        TaskModuleConfigResponse response = new TaskModuleConfigResponse(
                CONFIG_ID, TASK_ID, true, false, true, false, true);
        when(configService.getConfig(TASK_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/tasks/{taskId}/module-config", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$.taskId").value(TASK_ID.toString()))
                .andExpect(jsonPath("$.focusEnabled").value(true))
                .andExpect(jsonPath("$.cycleEnabled").value(false));
    }

    @Test
    @WithMockUser
    void getConfig_whenNotFound_returns404() throws Exception {
        when(configService.getConfig(TASK_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{taskId}/module-config", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void upsertConfig_returnsOk() throws Exception {
        TaskModuleConfigRequest request = new TaskModuleConfigRequest(true, true, null, null, null);
        TaskModuleConfigResponse response = new TaskModuleConfigResponse(
                CONFIG_ID, TASK_ID, true, true, false, false, false);
        when(configService.upsertConfig(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(put("/tasks/{taskId}/module-config", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusEnabled").value(true))
                .andExpect(jsonPath("$.cycleEnabled").value(true));
    }

    @Test
    @WithMockUser
    void upsertConfig_whenTaskNotFound_returns404() throws Exception {
        when(configService.upsertConfig(eq(TASK_ID), any(), eq(USER_ID)))
                .thenThrow(new IllegalArgumentException("task not found"));

        mockMvc.perform(put("/tasks/{taskId}/module-config", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskModuleConfigRequest(null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }
}
