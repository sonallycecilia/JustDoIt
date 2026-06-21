package com.justdoit.schedule.feature.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WeeklySummaryRepository extends JpaRepository<WeeklySummary, UUID> {
    Optional<WeeklySummary> findByWeeklyPlanId(UUID weeklyPlanId);
}
