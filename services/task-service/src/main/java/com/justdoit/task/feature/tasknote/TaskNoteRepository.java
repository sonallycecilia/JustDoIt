package com.justdoit.task.feature.tasknote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

// interface que faz a comunicação direta com o banco de dados, usando o Spring Data JPA.

public interface TaskNoteRepository extends JpaRepository<TaskNote, UUID> {
    Optional<TaskNote> findByTaskId(UUID taskId);
    // faz a busca pelo id da tarefa 
}
