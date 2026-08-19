package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.Task; // Import corrigido
import com.justdoit.task.feature.task.TaskEstimates;
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
    private final WeeklyCycleRepository cycleRepository;

    public WeeklyClosurePreviewService(TaskRepository taskRepository,
                                       WeeklyCycleProvisioningService cycleProvisioningService,
                                       WeeklyCycleRepository cycleRepository) {
        this.taskRepository = taskRepository;
        this.cycleProvisioningService = cycleProvisioningService;
        this.cycleRepository = cycleRepository;
    }

    // Não é mais readOnly: getOrCreateCurrentCycle pode precisar criar o
    // primeiro ciclo do usuário na hora (INSERT) quando ele ainda não existe.
    @Transactional
    public ClosurePreviewDTO getClosurePreview(UUID userId) {
        // Uma semana expirada aguardando triagem tem precedência sobre a nova
        // semana. Antes ela era ignorada porque o provisionamento procura apenas
        // OPEN e criava outro ciclo, deixando o histórico antigo órfão.
        WeeklyCycle currentCycle = cycleRepository
                .findFirstByUserIdAndStatusOrderByStartDateDesc(userId, CycleStatus.PENDING_REVIEW)
                .orElseGet(() -> cycleProvisioningService.getOrCreateCurrentCycle(userId));

        List<Task> currentTasks = taskRepository.findAllByCycleId(currentCycle.getId());

        List<TaskSummaryDTO> pendingTasks = currentTasks.stream()
                .filter(this::isPending)
                .map(task -> new TaskSummaryDTO(task.getId(), task.getTitle(), task.getStatus(),
                        TaskEstimates.minutesOf(task)))
                .collect(Collectors.toList());

        List<TaskSummaryDTO> completedTasks = currentTasks.stream()
                .filter(task -> !isPending(task))
                .map(task -> new TaskSummaryDTO(task.getId(), task.getTitle(), task.getStatus(),
                        TaskEstimates.minutesOf(task)))
                .collect(Collectors.toList());

        return new ClosurePreviewDTO(
                currentCycle.getId(),
                currentCycle.getStartDate().toLocalDate(),
                currentCycle.getEndDate().toLocalDate(),
                pendingTasks,
                completedTasks
        );
    }

    private boolean isPending(Task task) {
        return task.getStatus() == null 
            || task.getStatus() == TaskStatus.PENDING 
            || task.getStatus() == TaskStatus.IN_PROGRESS;
    }
}
