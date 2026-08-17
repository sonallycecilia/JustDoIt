package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.Task; // Import corrigido
import com.justdoit.task.feature.task.TaskRepository; // Import corrigido
import com.justdoit.task.shared.TaskStatus; // Import do Enum
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class WeeklyClosurePreviewService {

    private final TaskRepository taskRepository;
    private final WeeklyCycleProvisioningService cycleProvisioningService;

    public WeeklyClosurePreviewService(TaskRepository taskRepository, WeeklyCycleProvisioningService cycleProvisioningService) {
        this.taskRepository = taskRepository;
        this.cycleProvisioningService = cycleProvisioningService;
    }

    // Não é mais readOnly: getOrCreateCurrentCycle pode precisar criar o
    // primeiro ciclo do usuário na hora (INSERT) quando ele ainda não existe.
    @Transactional
    public ClosurePreviewDTO getClosurePreview(UUID userId) {
        WeeklyCycle currentCycle = cycleProvisioningService.getOrCreateCurrentCycle(userId);

        List<Task> currentTasks = taskRepository.findAllByCycleId(currentCycle.getId());

        List<TaskSummaryDTO> pendingTasks = currentTasks.stream()
                .filter(this::isPending)
                .map(task -> new TaskSummaryDTO(task.getId(), task.getTitle(), task.getStatus(), task.getEstimatedMinutes()))
                .collect(Collectors.toList());

        List<TaskSummaryDTO> completedTasks = currentTasks.stream()
                .filter(task -> !isPending(task))
                .map(task -> new TaskSummaryDTO(task.getId(), task.getTitle(), task.getStatus(), task.getEstimatedMinutes()))
                .collect(Collectors.toList());

        return new ClosurePreviewDTO(currentCycle.getId(), pendingTasks, completedTasks);
    }

    private boolean isPending(Task task) {
        return task.getStatus() == null 
            || task.getStatus() == TaskStatus.PENDING 
            || task.getStatus() == TaskStatus.IN_PROGRESS;
    }
}