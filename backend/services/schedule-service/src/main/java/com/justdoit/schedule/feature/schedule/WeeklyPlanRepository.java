package com.justdoit.schedule.feature.schedule;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WeeklyPlanRepository extends JpaRepository<WeeklyPlan, UUID> {
    List<WeeklyPlan> findByUserId(UUID userId);
    Optional<WeeklyPlan> findByIdAndUserId(UUID id, UUID userId);

    // Como o frontend acha o plano de uma semana: ele conhece a data da segunda,
    // não o id. Sem isto, o id só existiria na resposta do POST e se perderia
    // no primeiro reload.
    Optional<WeeklyPlan> findByUserIdAndWeekStartDate(UUID userId, LocalDate weekStartDate);
    List<WeeklyPlan> findByUserIdAndWeekStartDateBetweenOrderByWeekStartDateDesc(
            UUID userId, LocalDate from, LocalDate to);
}
