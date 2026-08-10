package com.justdoit.task.feature.weeklyclosure.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index; 
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "weekly_task_snapshots", indexes = {
    @Index(name = "idx_task_snapshot_cycle", columnList = "cycle_id")
})
public class WeeklyTaskSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "cycle_id", nullable = false)
    private UUID cycleId;

    @Column(name = "original_task_id", nullable = false)
    private UUID originalTaskId;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_at_closure", nullable = false)
    private TaskStatusAtClosure statusAtClosure;

    @Column(nullable = false)
    private Integer points;

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "updated_at")
    private LocalDateTime updatedAt = LocalDateTime.now();

    public WeeklyTaskSnapshot() {
    }

    public WeeklyTaskSnapshot(UUID id, UUID cycleId, UUID originalTaskId, String title, TaskStatusAtClosure statusAtClosure, Integer points, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.cycleId = cycleId;
        this.originalTaskId = originalTaskId;
        this.title = title;
        this.statusAtClosure = statusAtClosure;
        this.points = points;
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

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public TaskStatusAtClosure getStatusAtClosure() {
        return statusAtClosure;
    }

    public void setStatusAtClosure(TaskStatusAtClosure statusAtClosure) {
        this.statusAtClosure = statusAtClosure;
    }

    public Integer getPoints() {
        return points;
    }

    public void setPoints(Integer points) {
        this.points = points;
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