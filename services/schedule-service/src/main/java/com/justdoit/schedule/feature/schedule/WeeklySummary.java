package com.justdoit.schedule.feature.schedule;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "weekly_summary")
public class WeeklySummary {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "weekly_plan_id", unique = true, nullable = false)
    private WeeklyPlan weeklyPlan;

    @Builder.Default
    @Column(name = "total_estimated_minutes")
    private Integer totalEstimatedMinutes = 0;

    @Builder.Default
    @Column(name = "total_actual_seconds")
    private Long totalActualSeconds = 0L;

    @Builder.Default
    @Column(name = "deviation_seconds")
    private Long deviationSeconds = 0L;

    @Builder.Default
    @Column(name = "completed_tasks")
    private Integer completedTasks = 0;

    @Builder.Default
    @Column(name = "total_tasks")
    private Integer totalTasks = 0;
}
