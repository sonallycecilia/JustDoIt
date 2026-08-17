package com.justdoit.schedule.feature.schedule;

import com.justdoit.schedule.shared.ScheduleStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
// Uma semana só pode ter um plano por usuário. Sem isto, recarregar a página e
// postar de novo criaria planos duplicados para a mesma semana, cada um com o
// seu resumo. O findOrCreate do serviço resolve o caso comum; o índice é quem
// garante sob concorrência.
@Table(name = "weekly_plan",
       uniqueConstraints = @UniqueConstraint(name = "uk_weekly_plan_user_week",
                                             columnNames = {"user_id", "week_start_date"}))
public class WeeklyPlan {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "week_start_date", nullable = false)
    private LocalDate weekStartDate;

    @Column(name = "week_end_date", nullable = false)
    private LocalDate weekEndDate;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ScheduleStatus status = ScheduleStatus.OPEN;

    @OneToOne(mappedBy = "weeklyPlan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private WeeklySummary summary;
}
