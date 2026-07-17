package com.justdoit.task.feature.note;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NoteRepository extends JpaRepository<Note, UUID> {

    List<Note> findByUserIdOrderByPinnedDescUpdatedAtDesc(UUID userId);

    Optional<Note> findByUserIdAndPinnedTrue(UUID userId);

    Optional<Note> findByIdAndUserId(UUID id, UUID userId);

    void deleteByUserId(UUID userId);
}
