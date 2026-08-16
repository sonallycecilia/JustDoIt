package com.justdoit.notification.feature.support;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SupportMessageServiceTest {

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private final SupportMessageRepository repository = mock(SupportMessageRepository.class);
    private final SupportMessageService service = new SupportMessageService(repository);

    @Test
    void sendPersistsTrimmedUserMessageAndContext() {
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.send(new SupportMessageRequest(
                "  Preciso de ajuda  ", " https://app/todo ", " Test Browser "), USER_ID);

        ArgumentCaptor<SupportMessage> captor = ArgumentCaptor.forClass(SupportMessage.class);
        verify(repository).save(captor.capture());
        SupportMessage saved = captor.getValue();
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getSender()).isEqualTo(SupportMessageSender.USER);
        assertThat(saved.getContent()).isEqualTo("Preciso de ajuda");
        assertThat(saved.getPageUrl()).isEqualTo("https://app/todo");
        assertThat(saved.getUserAgent()).isEqualTo("Test Browser");
    }

    @Test
    void getConversationReturnsMessagesInRepositoryOrder() {
        SupportMessage message = SupportMessage.builder()
                .id(UUID.randomUUID())
                .userId(USER_ID)
                .sender(SupportMessageSender.DEVELOPMENT)
                .content("Recebemos sua mensagem")
                .createdAt(LocalDateTime.of(2026, 8, 15, 10, 0))
                .build();
        when(repository.findByUserIdOrderByCreatedAtAsc(USER_ID)).thenReturn(List.of(message));

        List<SupportMessageResponse> result = service.getConversation(USER_ID);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().sender()).isEqualTo(SupportMessageSender.DEVELOPMENT);
        assertThat(result.getFirst().content()).isEqualTo("Recebemos sua mensagem");
    }
}
