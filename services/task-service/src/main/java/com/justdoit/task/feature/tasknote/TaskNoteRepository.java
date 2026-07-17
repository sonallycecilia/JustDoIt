package com.justdoit.task.feature.tasknote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TaskNoteRepository extends JpaRepository<TaskNote, UUID> {
    Optional<TaskNote> findByTaskId(UUID taskId);
}
