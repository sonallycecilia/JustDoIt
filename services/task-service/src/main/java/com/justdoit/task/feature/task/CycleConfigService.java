package com.justdoit.task.feature.task;

import com.justdoit.task.shared.CycleConfigRequest;
import com.justdoit.task.shared.CycleConfigResponse;
import com.justdoit.task.shared.CycleType;
import com.justdoit.task.shared.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CycleConfigService {

    private final TaskRepository taskRepository;
    private final CycleConfigRepository cycleConfigRepository;
    private final CycleMaterializer materializer;

    public CycleConfigResponse getCycleConfig(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        CycleConfig config = cycleConfigRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Cycle config not found"));
        return toResponse(config);
    }

    @Transactional
    public CycleConfigResponse upsertCycleConfig(UUID taskId, CycleConfigRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        validarCustom(request);
        CycleConfig config = cycleConfigRepository.findByTaskId(taskId)
                .orElse(CycleConfig.builder().task(task).build());
        config.setCycleType(request.cycleType());
        if (request.startDate() != null) config.setStartDate(request.startDate());
        if (request.endDate() != null) config.setEndDate(request.endDate());
        if (request.nextResetDate() != null) config.setNextResetDate(request.nextResetDate());
        // Campos do ciclo personalizado (setados diretamente; nulos p/ presets).
        config.setIntervalUnit(request.intervalUnit());
        config.setIntervalCount(request.intervalCount());
        config.setTotalOccurrences(request.totalOccurrences());
        config.setStartTime(request.startTime());
        CycleConfig salvo = cycleConfigRepository.save(config);
        // Gera JÁ as ocorrências futuras (poucas, limitadas por quantidade) para
        // aparecerem no calendário assim que a recorrência é criada. Idempotente.
        materializer.materialize(salvo);
        return toResponse(salvo);
    }

    @Transactional
    public void deleteCycleConfig(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        // Limpa as ocorrências FUTURAS ainda pendentes geradas por esta série
        // (não mexe nas já concluídas nem nas passadas — preservam o histórico).
        taskRepository.deleteAll(taskRepository.findBySeriesIdAndStatusAndDueDateGreaterThanEqual(
                taskId, TaskStatus.PENDING, LocalDate.now()));
        cycleConfigRepository.findByTaskId(taskId).ifPresent(cycleConfigRepository::delete);
    }

    /** Teto de repetições de um ciclo custom (mantém a materialização curta e limitada). */
    static final int MAX_OCCURRENCES = 365;

    // Ciclo personalizado exige intervalo e quantidade válidos; presets ignoram.
    private void validarCustom(CycleConfigRequest request) {
        if (request.cycleType() != CycleType.CUSTOM) return;
        if (request.intervalUnit() == null
                || request.intervalCount() == null || request.intervalCount() <= 0
                || request.totalOccurrences() == null || request.totalOccurrences() <= 0) {
            throw new IllegalArgumentException(
                    "Ciclo CUSTOM exige intervalUnit, intervalCount > 0 e totalOccurrences > 0");
        }
        if (request.totalOccurrences() > MAX_OCCURRENCES) {
            throw new IllegalArgumentException("totalOccurrences não pode passar de " + MAX_OCCURRENCES);
        }
    }

    private CycleConfigResponse toResponse(CycleConfig config) {
        return new CycleConfigResponse(
                config.getId(),
                config.getTask().getId(),
                config.getCycleType(),
                config.getStartDate(),
                config.getEndDate(),
                config.getNextResetDate(),
                config.getIntervalUnit(),
                config.getIntervalCount(),
                config.getTotalOccurrences(),
                config.getStartTime()
        );
    }
}
