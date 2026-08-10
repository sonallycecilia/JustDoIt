package com.justdoit.task.feature.task;

import com.justdoit.task.shared.SubTaskRequest;
import com.justdoit.task.shared.SubTaskResponse;
import com.justdoit.task.shared.TaskRequest;
import com.justdoit.task.shared.TaskResponse;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskStatus;
import com.justdoit.task.shared.DeleteScope;
import com.justdoit.task.feature.category.Category;
import com.justdoit.task.feature.category.CategoryRepository;
import com.justdoit.task.feature.cycle.CycleConfig;
import com.justdoit.task.feature.cycle.CycleConfigRepository;
import com.justdoit.task.feature.weeklyclosure.domain.CycleMutabilityGuard;
import com.justdoit.task.feature.weeklyclosure.domain.WeeklyCycleProvisioningService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final CategoryRepository categoryRepository;
    private final CycleConfigRepository cycleConfigRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final CycleMutabilityGuard cycleMutabilityGuard;
    private final WeeklyCycleProvisioningService cycleProvisioningService;

    @Transactional
    public TaskResponse createTask(TaskRequest request, UUID userId) {
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        }
        // Toda tarefa nova precisa pertencer ao ciclo semanal OPEN vigente do
        // usuário. Sem isso, ela nunca aparece na prévia/fechamento semanal
        // (que filtram por cycle_id) e o guard de imutabilidade nunca a trava
        // quando o ciclo fecha.
        UUID currentCycleId = cycleProvisioningService.getOrCreateCurrentCycle(userId).getId();
        Task task = Task.builder()
                .userId(userId)
                .cycleId(currentCycleId)
                .category(category)
                .title(request.title())
                .description(request.description())
                .dueDate(request.dueDate())
                .dueTime(request.dueTime())
                .priority(request.priority() != null ? request.priority() : Priority.NORMAL)
                .status(TaskStatus.PENDING)
                .build();
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse updateTask(UUID taskId, TaskRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        cycleMutabilityGuard.ensureTaskIsMutable(task.getCycleId());

        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            task.setCategory(category);
        } else {
            task.setCategory(null);
        }
        
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setDueTime(request.dueTime());
        
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID userId, DeleteScope scope) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        cycleMutabilityGuard.ensureTaskIsMutable(task.getCycleId());

        if (scope == DeleteScope.SERIES) {
            UUID rootId = task.getSeriesId() != null ? task.getSeriesId() : task.getId();
            Task root = task.getSeriesId() == null
                    ? task
                    : taskRepository.findByIdAndUserId(rootId, userId)
                        .orElseThrow(() -> new IllegalArgumentException("Task series not found"));
            
            cycleMutabilityGuard.ensureTaskIsMutable(root.getCycleId());

            List<Task> seriesTasks = taskRepository.findBySeriesIdAndUserId(rootId, userId);
            for (Task t : seriesTasks) {
                cycleMutabilityGuard.ensureTaskIsMutable(t.getCycleId());
            }

            taskRepository.deleteAll(seriesTasks);
            taskRepository.delete(root);
            return;
        }
        if (task.getSeriesId() == null && task.getCycleConfig() != null) {
            promoteNextOccurrenceAndDeleteRoot(task, userId);
            return;
        }
        taskRepository.delete(task);
    }

    private void promoteNextOccurrenceAndDeleteRoot(Task root, UUID userId) {
        List<Task> occurrences = taskRepository.findBySeriesIdAndUserId(root.getId(), userId);
        if (occurrences.isEmpty()) {
            taskRepository.delete(root);
            return;
        }

        Task promoted = occurrences.stream()
                .min(Comparator
                        .comparing((Task t) -> t.getDueDate() != null ? t.getDueDate() : LocalDate.MAX)
                        .thenComparing(t -> t.getDueTime() != null ? t.getDueTime() : LocalTime.MAX))
                .orElseThrow();
        CycleConfig config = root.getCycleConfig();

        root.setCycleConfig(null);
        promoted.setSeriesId(null);
        promoted.setCycleConfig(config);
        config.setTask(promoted);
        occurrences.stream()
                .filter(t -> !t.getId().equals(promoted.getId()))
                .forEach(t -> t.setSeriesId(promoted.getId()));

        taskRepository.saveAll(occurrences);
        cycleConfigRepository.save(config);
        taskRepository.delete(root);
    }

    public TaskResponse getTaskById(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserIdWithCycle(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return toResponse(task);
    }

    public List<TaskResponse> getAllTasksByUser(UUID userId) {
        return taskRepository.findByUserIdWithCycle(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse completeTask(UUID taskId, UUID userId, String authorizationHeader) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        cycleMutabilityGuard.ensureTaskIsMutable(task.getCycleId());

        task.setStatus(TaskStatus.COMPLETED);
        task.setCompletedAt(LocalDateTime.now());
        TaskResponse response = toResponse(taskRepository.save(task));
        
        eventPublisher.publishEvent(new TaskCompletedEvent(task.getId(), task.getTitle(), authorizationHeader));
        return response;
    }

    @Transactional
    public TaskResponse reopenTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        cycleMutabilityGuard.ensureTaskIsMutable(task.getCycleId());

        task.setStatus(TaskStatus.PENDING);
        task.setCompletedAt(null);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public SubTaskResponse addSubTask(UUID taskId, SubTaskRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        cycleMutabilityGuard.ensureTaskIsMutable(task.getCycleId());

        SubTask subTask = SubTask.builder()
                .task(task)
                .title(request.title())
                .status(TaskStatus.PENDING)
                .position(request.position())
                .build();
        return toSubTaskResponse(subTaskRepository.save(subTask));
    }

    public List<SubTaskResponse> getSubTasks(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return subTaskRepository.findByTaskIdOrderByPosition(taskId).stream()
                .map(this::toSubTaskResponse)
                .toList();
    }

    @Transactional
    public SubTaskResponse toggleSubTask(UUID taskId, UUID subTaskId, UUID userId) {
        SubTask sub = findOwnedSubTask(taskId, subTaskId, userId);
        sub.setStatus(sub.getStatus() == TaskStatus.COMPLETED ? TaskStatus.PENDING : TaskStatus.COMPLETED);
        return toSubTaskResponse(subTaskRepository.save(sub));
    }

    @Transactional
    public void deleteSubTask(UUID taskId, UUID subTaskId, UUID userId) {
        subTaskRepository.delete(findOwnedSubTask(taskId, subTaskId, userId));
    }

    private SubTask findOwnedSubTask(UUID taskId, UUID subTaskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        
        cycleMutabilityGuard.ensureTaskIsMutable(task.getCycleId());

        return subTaskRepository.findById(subTaskId)
                .filter(s -> s.getTask().getId().equals(taskId))
                .orElseThrow(() -> new IllegalArgumentException("SubTask not found"));
    }

    public double getSubTaskProgress(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        long total = subTaskRepository.countByTaskId(taskId);
        if (total == 0) return 0.0;
        long completed = subTaskRepository.countByTaskIdAndStatus(taskId, TaskStatus.COMPLETED);
        return (double) completed / total;
    }

    private TaskResponse toResponse(Task task) {
        return new TaskResponse(
                task.getId(),
                task.getUserId(),
                task.getCategory() != null ? task.getCategory().getId() : null,
                task.getTitle(),
                task.getDescription(),
                TaskEstimates.minutesOf(task),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getDueTime(),
                task.getCreatedAt(),
                task.getUpdatedAt(),
                task.getSeriesId(),
                task.getCycleConfig() != null ? task.getCycleConfig().getCycleType() : null
        );
    }

    private SubTaskResponse toSubTaskResponse(SubTask sub) {
        return new SubTaskResponse(sub.getId(), sub.getTitle(), sub.getStatus(), sub.getPosition());
    }
}