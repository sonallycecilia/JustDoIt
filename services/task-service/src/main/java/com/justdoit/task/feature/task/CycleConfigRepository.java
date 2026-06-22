package com.justdoit.task.feature.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CycleConfigRepository extends JpaRepository<CycleConfig, UUID> {
    Optional<CycleConfig> findByTaskId(UUID taskId);
}
