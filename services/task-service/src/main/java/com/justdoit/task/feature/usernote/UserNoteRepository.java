package com.justdoit.task.feature.usernote;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserNoteRepository extends JpaRepository<UserNote, UUID> {
    Optional<UserNote> findByUserId(UUID userId);
}