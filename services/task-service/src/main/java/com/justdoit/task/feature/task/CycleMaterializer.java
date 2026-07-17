package com.justdoit.task.feature.task;

import com.justdoit.task.shared.CycleType;
import com.justdoit.task.shared.IntervalUnit;
import com.justdoit.task.shared.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Materializa as ocorrências futuras de uma tarefa cíclica como tarefas REAIS
 * (com data), para aparecerem no calendário/lista assim que a recorrência é criada.
 *
 * LIMITE POR QUANTIDADE (não por dias): mantém no máximo {@link #MAX_FUTURAS}
 * ocorrências futuras por série. Assim diário, semanal ou quinzenal geram sempre
 * o mesmo número pequeno — nunca uma enxurrada. A contagem torna a geração
 * idempotente: chamar de novo (na criação, ao re-salvar, no job diário) não passa
 * do limite. Conforme as ocorrências vão passando/sendo concluídas, o job repõe.
 *
 * A tarefa que possui o {@link CycleConfig} é o MODELO e conta como a 1ª ocorrência.
 * As geradas recebem {@code seriesId = id do modelo} (o modelo fica com seriesId nulo).
 */
@Service
@RequiredArgsConstructor
public class CycleMaterializer {

    private final TaskRepository taskRepository;
    private final CycleConfigRepository cycleConfigRepository;

    /** Quantas ocorrências futuras (geradas) manter por série. Poucas de propósito. */
    public static final int MAX_FUTURAS = 4;
    /** Trava contra laços patológicos ao "alcançar" cursores muito antigos. */
    private static final int MAX_ITERACOES = 500;

    @Transactional
    public int materialize(CycleConfig config) {
        return config.getCycleType() == CycleType.CUSTOM
                ? materializeCustom(config)
                : materializePreset(config);
    }

    // ── Presets (DAILY/WEEKLY/…): granularidade de dia, encerra por endDate ──────
    private int materializePreset(CycleConfig config) {
        Task modelo = config.getTask();
        UUID serie = modelo.getId();
        LocalDate hoje = LocalDate.now();

        long existentes = taskRepository
                .countBySeriesIdAndStatusAndDueDateGreaterThanEqual(serie, TaskStatus.PENDING, hoje);
        long faltam = MAX_FUTURAS - existentes;
        if (faltam <= 0) {
            return 0; // já há ocorrências futuras suficientes
        }

        // Continua a partir da última data já existente (geradas ou o próprio modelo).
        LocalDate ultima = taskRepository.findMaxDueDateBySeriesId(serie);
        if (ultima == null) ultima = modelo.getDueDate();
        if (ultima == null) ultima = hoje; // modelo sem data → ancora em hoje

        LocalDate endDate = config.getEndDate();
        LocalDate proxima = config.getCycleType().advance(ultima);

        int criadas = 0, guarda = 0;
        while (criadas < faltam
                && (endDate == null || !proxima.isAfter(endDate))
                && guarda++ < MAX_ITERACOES) {
            // Só materializa datas de hoje em diante (não cria ocorrência já vencida).
            if (!proxima.isBefore(hoje)) {
                taskRepository.save(ocorrencia(modelo, proxima, modelo.getDueTime(), serie));
                criadas++;
            }
            proxima = config.getCycleType().advance(proxima);
        }

        // Guarda a próxima data prevista (informativo) e encerra se passou do endDate.
        config.setNextResetDate((endDate != null && proxima.isAfter(endDate)) ? null : proxima);
        cycleConfigRepository.save(config);
        return criadas;
    }

    // ── CUSTOM: "a cada intervalCount [horas|dias], totalOccurrences vezes" ──────
    // Ocorrência k (0 = modelo) fica em anchor + k*intervalo. Materializa a janela
    // futura (MAX_FUTURAS) via checagem de existência por (série, data, hora), o que
    // torna o método idempotente e correto mesmo pulando ocorrências já passadas.
    private int materializeCustom(CycleConfig config) {
        Task modelo = config.getTask();
        UUID serie = modelo.getId();
        LocalDate hoje = LocalDate.now();

        IntervalUnit unit = config.getIntervalUnit();
        int step = config.getIntervalCount() != null ? config.getIntervalCount() : 1;
        int total = config.getTotalOccurrences() != null ? config.getTotalOccurrences() : 1;
        if (unit == null || step <= 0 || total <= 1) {
            return 0; // nada a materializar (config incompleto ou série de 1 ocorrência)
        }

        // Âncora (ocorrência 0 = modelo). Data/hora de partida do ciclo.
        LocalDate baseDate = config.getStartDate() != null ? config.getStartDate()
                : (modelo.getDueDate() != null ? modelo.getDueDate() : hoje);
        LocalTime baseTime = config.getStartTime() != null ? config.getStartTime()
                : (modelo.getDueTime() != null ? modelo.getDueTime() : LocalTime.MIDNIGHT);
        LocalDateTime anchor = LocalDateTime.of(baseDate, baseTime);

        long existentesFuturas = taskRepository
                .countBySeriesIdAndStatusAndDueDateGreaterThanEqual(serie, TaskStatus.PENDING, hoje);
        long faltam = MAX_FUTURAS - existentesFuturas;
        if (faltam <= 0) {
            return 0;
        }

        int criadas = 0;
        LocalDateTime proximaPrevista = null;
        // k vai de 1 até total-1 (k=0 é o modelo). Limitado por total → nunca passa
        // da quantidade pedida. O laço é curto: total é limitado no service.
        for (int k = 1; k < total && criadas < faltam; k++) {
            LocalDateTime dt = (unit == IntervalUnit.HOURS)
                    ? anchor.plusHours((long) step * k)
                    : anchor.plusDays((long) step * k);
            if (dt.toLocalDate().isBefore(hoje)) continue; // ocorrência já passou

            LocalDate d = dt.toLocalDate();
            LocalTime t = (unit == IntervalUnit.HOURS) ? dt.toLocalTime() : modelo.getDueTime();
            if (taskRepository.existsOccurrence(serie, d, t)) continue; // idempotência

            taskRepository.save(ocorrencia(modelo, d, t, serie));
            criadas++;
            if (proximaPrevista == null) proximaPrevista = dt;
        }

        // Informativo: a 1ª ocorrência que ainda não coube na janela (ou null se a
        // série já está completa/materializada).
        config.setNextResetDate(proximaPrevista != null ? proximaPrevista.toLocalDate() : null);
        cycleConfigRepository.save(config);
        return criadas;
    }

    // Ocorrência = cópia do modelo COM data/hora (aparece no calendário), marcada com
    // a série. Não leva subtarefas/timer/nota/cycle-config — só os campos base.
    private Task ocorrencia(Task modelo, LocalDate data, LocalTime hora, UUID serie) {
        return Task.builder()
                .userId(modelo.getUserId())
                .seriesId(serie)
                .category(modelo.getCategory())
                .title(modelo.getTitle())
                .description(modelo.getDescription())
                .priority(modelo.getPriority())
                .status(TaskStatus.PENDING)
                .dueDate(data)
                .dueTime(hora)
                .build();
    }
}
