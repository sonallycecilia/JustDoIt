package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WeeklyCycleRepository extends JpaRepository<WeeklyCycle, UUID> {
    
    Optional<WeeklyCycle> findFirstByUserIdAndStatusOrderByStartDateDesc(UUID userId, CycleStatus status);
    Optional<WeeklyCycle> findByIdAndUserId(UUID id, UUID userId);
    List<WeeklyCycle> findByStatusAndEndDateBefore(CycleStatus status, LocalDateTime date);
    List<WeeklyCycle> findAllByUserIdAndStatusOrderByStartDateDesc(UUID userId, CycleStatus status);
}