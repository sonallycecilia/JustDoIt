package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import com.justdoit.task.shared.TaskModuleConfigRequest;
import com.justdoit.task.shared.TaskModuleConfigResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
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
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
    }

    @Test
    void getConfig_returnsOk() throws Exception {
        TaskModuleConfigResponse response = new TaskModuleConfigResponse(
                CONFIG_ID, TASK_ID, true, false, true, false, true);
        when(configService.getConfig(TASK_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/tasks/{taskId}/module-config", TASK_ID)
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$.taskId").value(TASK_ID.toString()))
                .andExpect(jsonPath("$.focusEnabled").value(true))
                .andExpect(jsonPath("$.cycleEnabled").value(false));
    }

    @Test
    void getConfig_whenNotFound_returns404() throws Exception {
        when(configService.getConfig(TASK_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{taskId}/module-config", TASK_ID)
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void upsertConfig_returnsOk() throws Exception {
        TaskModuleConfigRequest request = new TaskModuleConfigRequest(true, true, null, null, null);
        TaskModuleConfigResponse response = new TaskModuleConfigResponse(
                CONFIG_ID, TASK_ID, true, true, false, false, false);
        when(configService.upsertConfig(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(put("/tasks/{taskId}/module-config", TASK_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.focusEnabled").value(true))
                .andExpect(jsonPath("$.cycleEnabled").value(true));
    }

    @Test
    void upsertConfig_whenTaskNotFound_returns404() throws Exception {
        when(configService.upsertConfig(eq(TASK_ID), any(), eq(USER_ID)))
                .thenThrow(new IllegalArgumentException("task not found"));

        mockMvc.perform(put("/tasks/{taskId}/module-config", TASK_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskModuleConfigRequest(null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }
}
