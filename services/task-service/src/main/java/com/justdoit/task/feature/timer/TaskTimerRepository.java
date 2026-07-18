package com.justdoit.task.feature.timer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskTimerRepository extends JpaRepository<TaskTimer, UUID> {
    Optional<TaskTimer> findByTaskId(UUID taskId);
}
