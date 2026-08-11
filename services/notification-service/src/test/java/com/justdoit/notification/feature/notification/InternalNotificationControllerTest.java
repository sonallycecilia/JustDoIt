package com.justdoit.notification.feature.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import com.justdoit.notification.config.WebSecurityConfig;
import com.justdoit.notification.shared.InternalNotificationRequest;
import com.justdoit.notification.shared.NotificationResponse;
import com.justdoit.notification.shared.NotificationType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InternalNotificationController.class)
@Import(WebSecurityConfig.class)
@TestPropertySource(properties = {
        "app.internal-token=integration-secret",
        "app.cors.allowed-origins=http://localhost:3000"
})
class InternalNotificationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID NOTIFICATION_ID =
            UUID.fromString("00000000-0000-0000-0000-000000000002");

    @Test
    void validInternalToken_createsExportNotificationWithoutJwt() throws Exception {
        InternalNotificationRequest request = new InternalNotificationRequest(
                USER_ID, null, NotificationType.EXPORT_READY,
                "Exportação pronta", "Baixe em http://temporary-link");
        when(notificationService.createInternalNotification(any())).thenReturn(
                new NotificationResponse(NOTIFICATION_ID, USER_ID, null,
                        NotificationType.EXPORT_READY, request.title(), request.message(),
                        false, LocalDateTime.now()));

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", "integration-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("EXPORT_READY"));

        verify(notificationService).createInternalNotification(any());
    }

    @Test
    void missingOrInvalidInternalToken_isRejected() throws Exception {
        InternalNotificationRequest request = new InternalNotificationRequest(
                USER_ID, null, NotificationType.EXPORT_READY,
                "Exportação pronta", "Baixe em http://temporary-link");

        mockMvc.perform(post("/internal/notifications")
                        .header("X-Internal-Token", "wrong")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(notificationService, never()).createInternalNotification(any());
    }
}
