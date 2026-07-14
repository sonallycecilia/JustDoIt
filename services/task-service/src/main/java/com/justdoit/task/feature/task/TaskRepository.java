package com.justdoit.task.feature.task;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserId(UUID userId);
    Optional<Task> findByIdAndUserId(UUID id, UUID userId);
    List<Task> findByCategoryIdAndUserId(UUID categoryId, UUID userId);

    @Query("""
            SELECT COALESCE(SUM(t.estimatedMinutes), 0)
            FROM Task t
            WHERE t.userId = :userId
                AND t.dueDate = :dueDate
                AND (:excludedTaskId IS NULL OR t.id <> :excludedTaskId)
            """)
    Long sumEstimatedMinutesByUserIdAndDueDate(
            @Param("userId") UUID userId,
            @Param("dueDate") LocalDate dueDate,
            @Param("excludedTaskId") UUID excludedTaskId
    );
}
