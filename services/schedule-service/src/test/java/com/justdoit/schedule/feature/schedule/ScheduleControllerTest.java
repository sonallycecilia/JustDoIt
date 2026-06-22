package com.justdoit.schedule.feature.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.schedule.config.JwtUtil;
import com.justdoit.schedule.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ScheduleController.class)
class ScheduleControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private ScheduleService scheduleService;
    @MockBean private JwtUtil jwtUtil;

    private static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAN_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BLOCK_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private final LocalDate TODAY = LocalDate.of(2026, 6, 21);
    private final LocalDateTime START = LocalDateTime.of(2026, 6, 21, 9, 0);
    private final LocalDateTime END   = LocalDateTime.of(2026, 6, 21, 10, 0);

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
    }

    @Test
    @WithMockUser
    void createTimeBlock_returnsCreated() throws Exception {
        TimeBlockRequest request = new TimeBlockRequest(null, START, END, 60, TODAY);
        TimeBlockResponse response = new TimeBlockResponse(BLOCK_ID, USER_ID, null, START, END, 60, TODAY);
        when(scheduleService.createTimeBlock(any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(post("/time-blocks")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(BLOCK_ID.toString()))
                .andExpect(jsonPath("$.estimatedMinutes").value(60));
    }

    @Test
    @WithMockUser
    void createTimeBlock_missingStartDateTime_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/time-blocks")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"endDateTime\":\"2026-06-21T10:00:00\",\"date\":\"2026-06-21\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getTimeBlocksByDate_returnsOk() throws Exception {
        TimeBlockResponse response = new TimeBlockResponse(BLOCK_ID, USER_ID, null, START, END, 60, TODAY);
        when(scheduleService.getTimeBlocksByDate(TODAY, USER_ID)).thenReturn(List.of(response));

        mockMvc.perform(get("/time-blocks")
                        .header("Authorization", "Bearer mock-token")
                        .param("date", TODAY.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(BLOCK_ID.toString()));
    }

    @Test
    @WithMockUser
    void createWeeklyPlan_returnsCreated() throws Exception {
        WeeklyPlanRequest request = new WeeklyPlanRequest(TODAY, TODAY.plusDays(6));
        WeeklyPlanResponse response = new WeeklyPlanResponse(PLAN_ID, USER_ID, TODAY, TODAY.plusDays(6), ScheduleStatus.OPEN);
        when(scheduleService.createWeeklyPlan(any(), eq(USER_ID))).thenReturn(response);

        mockMvc.perform(post("/weekly-plans")
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(PLAN_ID.toString()))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    @WithMockUser
    void closeWeeklyPlan_returnsOk() throws Exception {
        WeeklyPlanResponse response = new WeeklyPlanResponse(PLAN_ID, USER_ID, TODAY, TODAY.plusDays(6), ScheduleStatus.CLOSED);
        when(scheduleService.closeWeeklyPlan(PLAN_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(patch("/weekly-plans/{id}/close", PLAN_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CLOSED"));
    }

    @Test
    @WithMockUser
    void closeWeeklyPlan_notFound_returns404() throws Exception {
        when(scheduleService.closeWeeklyPlan(PLAN_ID, USER_ID))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(patch("/weekly-plans/{id}/close", PLAN_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void generateWeeklySummary_returnsOk() throws Exception {
        WeeklySummaryResponse response = new WeeklySummaryResponse(
                UUID.randomUUID(), PLAN_ID, 60, null, null, null, 1);
        when(scheduleService.generateWeeklySummary(PLAN_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(post("/weekly-plans/{id}/summary", PLAN_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalEstimatedMinutes").value(60))
                .andExpect(jsonPath("$.totalTasks").value(1));
    }

    @Test
    @WithMockUser
    void generateWeeklySummary_notFound_returns404() throws Exception {
        when(scheduleService.generateWeeklySummary(PLAN_ID, USER_ID))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(post("/weekly-plans/{id}/summary", PLAN_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getWeeklySummary_returnsOk() throws Exception {
        WeeklySummaryResponse response = new WeeklySummaryResponse(
                UUID.randomUUID(), PLAN_ID, 120, null, null, null, 2);
        when(scheduleService.generateWeeklySummary(PLAN_ID, USER_ID)).thenReturn(response);

        mockMvc.perform(get("/weekly-plans/{id}/summary", PLAN_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTasks").value(2));
    }
}
