package com.justdoit.task.feature.task;

import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.TaskReportResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskReportController.class)
class TaskReportControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private TaskReportService taskReportService;
    @MockitoBean private JwtUtil jwtUtil;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    @Test
    @WithMockUser
    void getReport_returnsAggregates() throws Exception {
        LocalDate from = LocalDate.of(2026, 6, 29);
        LocalDate to = from.plusDays(6);
        TaskReportResponse response = new TaskReportResponse(from, to, 5, 3, 5400,
                List.of(new TaskReportResponse.DaySummary(from, 5400, 3)));
        when(taskReportService.getReport(eq(USER_ID), eq(from), eq(to))).thenReturn(response);

        mockMvc.perform(get("/tasks/report")
                        .param("from", "2026-06-29")
                        .param("to", "2026-07-05")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(5))
                .andExpect(jsonPath("$.completedTasks").value(3))
                .andExpect(jsonPath("$.totalActualSeconds").value(5400))
                .andExpect(jsonPath("$.byDay[0].date").value("2026-06-29"));
    }

    @Test
    @WithMockUser
    void getReport_invalidRange_returnsBadRequest() throws Exception {
        when(taskReportService.getReport(eq(USER_ID), any(), any()))
                .thenThrow(new IllegalArgumentException("Período inválido"));

        mockMvc.perform(get("/tasks/report")
                        .param("from", "2026-07-05")
                        .param("to", "2026-06-29")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getReport_missingParams_returnsBadRequest() throws Exception {
        mockMvc.perform(get("/tasks/report")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isBadRequest());
    }
}
