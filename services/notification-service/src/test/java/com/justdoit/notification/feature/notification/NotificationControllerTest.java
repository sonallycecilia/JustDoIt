package com.justdoit.notification.feature.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.notification.config.JwtUtil;
import com.justdoit.notification.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(NotificationController.class)
class NotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockBean private NotificationService notificationService;
    @MockBean private JwtUtil jwtUtil;

    private static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTIF_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PREF_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private NotificationResponse notifResponse;

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
        notifResponse = new NotificationResponse(NOTIF_ID, USER_ID, null,
                NotificationType.TASK_COMPLETED, "Task done", "Your task was completed",
                false, LocalDateTime.now());
    }

    @Test
    @WithMockUser
    void createNotification_returnsCreated() throws Exception {
        CreateNotificationRequest request = new CreateNotificationRequest(
                null, NotificationType.TASK_COMPLETED, "Task done", "Your task was completed");
        when(notificationService.createNotification(any(), eq(USER_ID))).thenReturn(notifResponse);

        mockMvc.perform(post("/notifications")
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(NOTIF_ID.toString()))
                .andExpect(jsonPath("$.title").value("Task done"))
                .andExpect(jsonPath("$.read").value(false));
    }

    @Test
    @WithMockUser
    void createNotification_missingTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateNotificationRequest(null, NotificationType.TASK_COMPLETED, "", "msg"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getAll_returnsOk() throws Exception {
        when(notificationService.getAllByUser(USER_ID)).thenReturn(List.of(notifResponse));

        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIF_ID.toString()));
    }

    @Test
    @WithMockUser
    void getUnread_returnsOk() throws Exception {
        when(notificationService.getUnreadByUser(USER_ID)).thenReturn(List.of(notifResponse));

        mockMvc.perform(get("/notifications/unread")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    @WithMockUser
    void markAsRead_returnsOk() throws Exception {
        NotificationResponse readResponse = new NotificationResponse(NOTIF_ID, USER_ID, null,
                NotificationType.TASK_COMPLETED, "Task done", "Your task was completed",
                true, LocalDateTime.now());
        when(notificationService.markAsRead(NOTIF_ID, USER_ID)).thenReturn(readResponse);

        mockMvc.perform(patch("/notifications/{id}/read", NOTIF_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    @WithMockUser
    void markAsRead_notFound_returns404() throws Exception {
        when(notificationService.markAsRead(NOTIF_ID, USER_ID))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(patch("/notifications/{id}/read", NOTIF_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void getPreferences_returnsOk() throws Exception {
        NotificationPreferenceResponse prefResponse = new NotificationPreferenceResponse(
                PREF_ID, USER_ID, true, true, true);
        when(notificationService.getOrCreatePreference(USER_ID)).thenReturn(prefResponse);

        mockMvc.perform(get("/notifications/preferences")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyOnComplete").value(true));
    }

    @Test
    @WithMockUser
    void updatePreferences_returnsOk() throws Exception {
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(false, true, true);
        NotificationPreferenceResponse prefResponse = new NotificationPreferenceResponse(
                PREF_ID, USER_ID, false, true, true);
        when(notificationService.updatePreference(eq(USER_ID), any())).thenReturn(prefResponse);

        mockMvc.perform(put("/notifications/preferences")
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyOnComplete").value(false))
                .andExpect(jsonPath("$.notifyOnOverdue").value(true));
    }
}
