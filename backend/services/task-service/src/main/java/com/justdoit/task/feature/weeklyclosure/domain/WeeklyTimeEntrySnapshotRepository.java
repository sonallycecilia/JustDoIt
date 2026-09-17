package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

@Repository
public interface WeeklyTimeEntrySnapshotRepository extends JpaRepository<WeeklyTimeEntrySnapshot, UUID> {
    List<WeeklyTimeEntrySnapshot> findAllByCycleId(UUID cycleId);

    @Query("SELECT COALESCE(SUM(s.timeLoggedMinutes), 0) FROM WeeklyTimeEntrySnapshot s WHERE s.originalTaskId = :taskId")
    Integer sumPreviousLoggedMinutesByTaskId(@Param("taskId") UUID taskId);
}
