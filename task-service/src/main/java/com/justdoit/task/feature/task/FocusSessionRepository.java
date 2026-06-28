package com.justdoit.task.feature.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {
    List<FocusSession> findByTaskId(UUID taskId);
    Optional<FocusSession> findByIdAndTaskId(UUID id, UUID taskId);
}
