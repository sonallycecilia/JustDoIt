package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.CycleConfigRequest;
import com.justdoit.task.shared.CycleConfigResponse;
import com.justdoit.task.shared.CycleType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CycleConfigController.class)
class CycleConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private CycleConfigService cycleConfigService;
    @MockBean private JwtUtil jwtUtil;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    @Test
    @WithMockUser
    void getCycleConfig_returnsOk() throws Exception {
        CycleConfigResponse response = new CycleConfigResponse(
                CONFIG_ID, TASK_ID, CycleType.WEEKLY,
                LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null);
        when(cycleConfigService.getCycleConfig(TASK_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/tasks/{taskId}/cycle-config", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(CONFIG_ID.toString()))
                .andExpect(jsonPath("$.cycleType").value("WEEKLY"))
                .andExpect(jsonPath("$.startDate").value("2025-01-01"));
    }

    @Test
    @WithMockUser
    void getCycleConfig_whenNotFound_returns404() throws Exception {
        when(cycleConfigService.getCycleConfig(TASK_ID, USER_ID))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{taskId}/cycle-config", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void upsertCycleConfig_returnsOk() throws Exception {
        CycleConfigRequest request = new CycleConfigRequest(CycleType.DAILY, LocalDate.of(2025, 6, 1), null, null);
        CycleConfigResponse response = new CycleConfigResponse(
                CONFIG_ID, TASK_ID, CycleType.DAILY, LocalDate.of(2025, 6, 1), null, null);
        when(cycleConfigService.upsertCycleConfig(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(put("/tasks/{taskId}/cycle-config", TASK_ID)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cycleType").value("DAILY"))
                .andExpect(jsonPath("$.startDate").value("2025-06-01"));
    }

    @Test
    @WithMockUser
    void upsertCycleConfig_withNullCycleType_returnsBadRequest() throws Exception {
        CycleConfigRequest request = new CycleConfigRequest(null, null, null, null);

        mockMvc.perform(put("/tasks/{taskId}/cycle-config", TASK_ID)
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
