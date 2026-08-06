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

    // AGENDADO: soma dos blocos que o usuário pôs no calendário nesta semana.
    @Builder.Default
    @Column(name = "total_scheduled_minutes")
    private Integer totalScheduledMinutes = 0;

    // ESTIMADO: soma das estimativas das tarefas que vencem na semana, vinda do
    // task-service. É outra coisa: uma tarefa pode ter estimativa sem nunca ter
    // entrado na agenda, e um bloco pode existir sem tarefa vinculada.
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
