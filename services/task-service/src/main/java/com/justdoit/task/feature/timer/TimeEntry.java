package com.justdoit.task.feature.timer;

import com.justdoit.task.feature.task.Task;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Um intervalo de trabalho cronometrado, COM data.
 *
 * TaskTimer.actualSeconds é um acumulado sem data: serve para mostrar "esta
 * tarefa já consumiu 3h", mas nenhum relatório por período consegue enxergá-lo
 * (não dá para saber se as 3h foram hoje ou no mês passado). Por isso o tempo do
 * cronômetro passou a ser gravado também aqui, um registro por intervalo.
 *
 * O acumulado continua existindo e é a fonte do número exibido na tarefa; estas
 * linhas são a fonte do recorte por dia no /tasks/report. Quem escreve nos dois
 * é o TaskTimerService, sempre junto, para que não divirjam.
 *
 * Espelha o modelo da FocusSession (Pomodoro), que já era datada — é por isso
 * que só o foco aparecia na Análise.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "time_entry", indexes = {
        @Index(name = "idx_time_entry_task", columnList = "task_id"),
        @Index(name = "idx_time_entry_started", columnList = "started_at")
})
public class TimeEntry {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    /** Duração do intervalo. Guardada, e não derivada de started/ended, porque o
     *  log manual (PATCH /timer/log) informa segundos sem um par exato de datas. */
    @Column(nullable = false)
    private long seconds;
}
