package com.justdoit.notification.feature.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportMessageController.class)
class SupportMessageControllerTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private SupportMessageService service;
    @MockitoBean private JwtValidator jwtValidator;

    @Test
    void getConversationReturnsAuthenticatedUsersMessages() throws Exception {
        when(service.getConversation(USER_ID)).thenReturn(List.of(new SupportMessageResponse(
                UUID.randomUUID(), SupportMessageSender.USER, "Olá", null,
                LocalDateTime.of(2026, 8, 15, 10, 0))));

        mockMvc.perform(get("/support/messages").with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].content").value("Olá"))
                .andExpect(jsonPath("$[0].sender").value("USER"));
    }

    @Test
    void sendReturnsCreatedMessage() throws Exception {
        SupportMessageRequest request = new SupportMessageRequest("Preciso de ajuda", null, null);
        when(service.send(any(SupportMessageRequest.class), eq(USER_ID))).thenReturn(new SupportMessageResponse(
                UUID.randomUUID(), SupportMessageSender.USER, request.content(), null, LocalDateTime.now()));

        mockMvc.perform(post("/support/messages")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Preciso de ajuda"));

        verify(service).send(any(SupportMessageRequest.class), eq(USER_ID));
    }

    @Test
    void sendRejectsBlankMessage() throws Exception {
        SupportMessageRequest request = new SupportMessageRequest("   ", null, null);

        mockMvc.perform(post("/support/messages")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.content").exists());

        verify(service, never()).send(any(), any());
    }

    @Test
    void endpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/support/messages"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/support/messages")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new SupportMessageRequest("Mensagem", null, null))))
                .andExpect(status().isUnauthorized());
    }
}
