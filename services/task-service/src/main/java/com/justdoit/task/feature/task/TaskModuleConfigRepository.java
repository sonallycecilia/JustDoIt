package com.justdoit.task.feature.task;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskModuleConfigRepository extends JpaRepository<TaskModuleConfig, UUID> {
    Optional<TaskModuleConfig> findByTaskId(UUID taskId);
}
