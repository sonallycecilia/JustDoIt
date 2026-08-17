package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WeeklyTaskSnapshotRepository extends JpaRepository<WeeklyTaskSnapshot, UUID> {
    List<WeeklyTaskSnapshot> findAllByCycleId(UUID cycleId);
}