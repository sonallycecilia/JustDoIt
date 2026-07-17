package com.justdoit.task.feature.task;

import com.justdoit.task.shared.CycleType;
import com.justdoit.task.shared.IntervalUnit;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cycle_config")
public class CycleConfig {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", unique = true, nullable = false)
    private Task task;

    @Enumerated(EnumType.STRING)
    @Column(name = "cycle_type", nullable = false)
    private CycleType cycleType;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "next_reset_date")
    private LocalDate nextResetDate;

    // ── Ciclo personalizado (cycleType == CUSTOM) ────────────────────────────
    // Todos nullable: presets não os usam. "a cada intervalCount intervalUnit,
    // totalOccurrences vezes, a partir de startDate[+startTime]".

    @Enumerated(EnumType.STRING)
    @Column(name = "interval_unit")
    private IntervalUnit intervalUnit;

    @Column(name = "interval_count")
    private Integer intervalCount;

    /** Total de ocorrências da série, INCLUINDO a tarefa-modelo (1ª). */
    @Column(name = "total_occurrences")
    private Integer totalOccurrences;

    /** Hora-âncora do ciclo custom em horas (ignorada quando a unidade é dias). */
    @Column(name = "start_time")
    private LocalTime startTime;
}
