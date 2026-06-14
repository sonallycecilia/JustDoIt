package com.justdoit.schedule.feature.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TimeBlockRepository extends JpaRepository<TimeBlock, UUID> {
    List<TimeBlock> findByUserIdAndDate(UUID userId, LocalDate date);
    List<TimeBlock> findByUserIdAndDateBetween(UUID userId, LocalDate startDate, LocalDate endDate);
    Optional<TimeBlock> findByIdAndUserId(UUID id, UUID userId);
}
