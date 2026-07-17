package com.justdoit.notification.feature.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import com.justdoit.notification.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
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
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID  = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTIF_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID PREF_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private NotificationResponse notifResponse;

    @BeforeEach
    void setUp() {
        notifResponse = new NotificationResponse(NOTIF_ID, USER_ID, null,
                NotificationType.TASK_COMPLETED, "Task done", "Your task was completed",
                false, LocalDateTime.now());
    }

    @Test
    void createNotification_returnsCreated() throws Exception {
        CreateNotificationRequest request = new CreateNotificationRequest(
                null, NotificationType.TASK_COMPLETED, "Task done", "Your task was completed");
        when(notificationService.createNotification(any(), eq(USER_ID))).thenReturn(notifResponse);

        mockMvc.perform(post("/notifications")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(NOTIF_ID.toString()))
                .andExpect(jsonPath("$.title").value("Task done"))
                .andExpect(jsonPath("$.read").value(false));
    }

    @Test
    void createNotification_missingTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/notifications")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new CreateNotificationRequest(null, NotificationType.TASK_COMPLETED, "", "msg"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getAll_returnsOk() throws Exception {
        when(notificationService.getAllByUser(USER_ID)).thenReturn(List.of(notifResponse));

        mockMvc.perform(get("/notifications")
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(NOTIF_ID.toString()));
    }

    @Test
    void getUnread_returnsOk() throws Exception {
        when(notificationService.getUnreadByUser(USER_ID)).thenReturn(List.of(notifResponse));

        mockMvc.perform(get("/notifications/unread")
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].read").value(false));
    }

    @Test
    void markAsRead_returnsOk() throws Exception {
        NotificationResponse readResponse = new NotificationResponse(NOTIF_ID, USER_ID, null,
                NotificationType.TASK_COMPLETED, "Task done", "Your task was completed",
                true, LocalDateTime.now());
        when(notificationService.markAsRead(NOTIF_ID, USER_ID)).thenReturn(readResponse);

        mockMvc.perform(patch("/notifications/{id}/read", NOTIF_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    @Test
    void markAsRead_notFound_returns404() throws Exception {
        when(notificationService.markAsRead(NOTIF_ID, USER_ID))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(patch("/notifications/{id}/read", NOTIF_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPreferences_returnsOk() throws Exception {
        NotificationPreferenceResponse prefResponse = new NotificationPreferenceResponse(
                PREF_ID, USER_ID, true, true, true);
        when(notificationService.getOrCreatePreference(USER_ID)).thenReturn(prefResponse);

        mockMvc.perform(get("/notifications/preferences")
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyOnComplete").value(true));
    }

    @Test
    void updatePreferences_returnsOk() throws Exception {
        NotificationPreferenceRequest request = new NotificationPreferenceRequest(false, true, true);
        NotificationPreferenceResponse prefResponse = new NotificationPreferenceResponse(
                PREF_ID, USER_ID, false, true, true);
        when(notificationService.updatePreference(eq(USER_ID), any())).thenReturn(prefResponse);

        mockMvc.perform(put("/notifications/preferences")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notifyOnComplete").value(false))
                .andExpect(jsonPath("$.notifyOnOverdue").value(true));
    }
}
