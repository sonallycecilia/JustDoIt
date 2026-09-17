package com.justdoit.notification.feature.support;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {
    List<SupportMessage> findByUserIdOrderByCreatedAtAsc(UUID userId);
}
