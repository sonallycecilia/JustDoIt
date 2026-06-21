package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskModuleConfigRequest;
import com.justdoit.task.shared.TaskModuleConfigResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskModuleConfigService {

    private final TaskRepository taskRepository;
    private final TaskModuleConfigRepository configRepository;

    public TaskModuleConfigResponse getConfig(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskModuleConfig config = configRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Module config not found"));
        return toResponse(config);
    }

    @Transactional
    public TaskModuleConfigResponse upsertConfig(UUID taskId, TaskModuleConfigRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskModuleConfig config = configRepository.findByTaskId(taskId)
                .orElse(TaskModuleConfig.builder().task(task).build());
        if (request.focusEnabled() != null) config.setFocusEnabled(request.focusEnabled());
        if (request.cycleEnabled() != null) config.setCycleEnabled(request.cycleEnabled());
        if (request.priorityEnabled() != null) config.setPriorityEnabled(request.priorityEnabled());
        if (request.timerEnabled() != null) config.setTimerEnabled(request.timerEnabled());
        if (request.notesEnabled() != null) config.setNotesEnabled(request.notesEnabled());
        return toResponse(configRepository.save(config));
    }

    private TaskModuleConfigResponse toResponse(TaskModuleConfig config) {
        return new TaskModuleConfigResponse(
                config.getId(),
                config.getTask().getId(),
                config.getFocusEnabled(),
                config.getCycleEnabled(),
                config.getPriorityEnabled(),
                config.getTimerEnabled(),
                config.getNotesEnabled()
        );
    }
}
