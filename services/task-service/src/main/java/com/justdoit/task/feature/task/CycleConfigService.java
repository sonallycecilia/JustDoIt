package com.justdoit.task.feature.task;

import com.justdoit.task.shared.CycleConfigRequest;
import com.justdoit.task.shared.CycleConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CycleConfigService {

    private final TaskRepository taskRepository;
    private final CycleConfigRepository cycleConfigRepository;

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
        CycleConfig config = cycleConfigRepository.findByTaskId(taskId)
                .orElse(CycleConfig.builder().task(task).build());
        config.setCycleType(request.cycleType());
        if (request.startDate() != null) config.setStartDate(request.startDate());
        if (request.endDate() != null) config.setEndDate(request.endDate());
        if (request.nextResetDate() != null) config.setNextResetDate(request.nextResetDate());
        return toResponse(cycleConfigRepository.save(config));
    }

    private CycleConfigResponse toResponse(CycleConfig config) {
        return new CycleConfigResponse(
                config.getId(),
                config.getTask().getId(),
                config.getCycleType(),
                config.getStartDate(),
                config.getEndDate(),
                config.getNextResetDate()
        );
    }
}
