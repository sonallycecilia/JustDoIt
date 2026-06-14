package com.justdoit.schedule.feature.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {
    List<WeeklyPlan> findByUserId(UUID userId);
    Optional<WeeklyPlan> findByIdAndUserId(UUID id, UUID userId);
}
