package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SubTaskRepository extends JpaRepository<SubTask, UUID> {
    List<SubTask> findByTaskIdOrderByPosition(UUID taskId);
    long countByTaskId(UUID taskId);
    long countByTaskIdAndStatus(UUID taskId, TaskStatus status);
}
