package com.justdoit.task.feature.weeklyclosure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index; 
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "weekly_time_entry_snapshots", indexes = {
    @Index(name = "idx_time_snapshot_cycle", columnList = "cycle_id")
})
public class WeeklyTimeEntrySnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "original_task_id", nullable = false)
    private UUID originalTaskId;

    @Column(name = "time_logged_minutes", nullable = false)
    private Integer timeLoggedMinutes;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WeeklyTimeEntrySnapshot() {
    }

    public WeeklyTimeEntrySnapshot(UUID id, UUID cycleId, UUID originalTaskId, Integer timeLoggedMinutes, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.cycleId = cycleId;
        this.originalTaskId = originalTaskId;
        this.timeLoggedMinutes = timeLoggedMinutes;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getCycleId() {
        return cycleId;
    }

    public void setCycleId(UUID cycleId) {
        this.cycleId = cycleId;
    }

    public UUID getOriginalTaskId() {
        return originalTaskId;
    }

    public void setOriginalTaskId(UUID originalTaskId) {
        this.originalTaskId = originalTaskId;
    }

    public Integer getTimeLoggedMinutes() {
        return timeLoggedMinutes;
    }

    public void setTimeLoggedMinutes(Integer timeLoggedMinutes) {
        this.timeLoggedMinutes = timeLoggedMinutes;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}