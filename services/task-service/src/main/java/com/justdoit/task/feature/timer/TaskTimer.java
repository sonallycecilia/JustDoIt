package com.justdoit.task.feature.timer;
import com.justdoit.task.feature.task.Task;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "task_timer")
public class TaskTimer {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", unique = true, nullable = false)
    private Task task;

    @Column(name = "estimated_minutes")
    private Integer estimatedMinutes;

    @Builder.Default
    @Column(name = "actual_seconds")
    private Long actualSeconds = 0L;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;
}
