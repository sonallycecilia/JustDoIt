package com.justdoit.notification.feature.support;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SupportMessageService {

    private final SupportMessageRepository repository;

    @Transactional(readOnly = true)
    public List<SupportMessageResponse> getConversation(UUID userId) {
        return repository.findByUserIdOrderByCreatedAtAsc(userId).stream()
                .map(SupportMessageResponse::from)
                .toList();
    }

    @Transactional
    public SupportMessageResponse send(SupportMessageRequest request, UUID userId) {
        SupportMessage message = SupportMessage.builder()
                .userId(userId)
                .sender(SupportMessageSender.USER)
                .content(request.content().trim())
                .pageUrl(blankToNull(request.pageUrl()))
                .userAgent(blankToNull(request.userAgent()))
                .build();
        return SupportMessageResponse.from(repository.save(message));
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
