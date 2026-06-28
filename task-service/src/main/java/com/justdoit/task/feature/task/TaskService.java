package com.justdoit.task.feature.task;

import com.justdoit.task.shared.SubTaskRequest;
import com.justdoit.task.shared.SubTaskResponse;
import com.justdoit.task.shared.TaskRequest;
import com.justdoit.task.shared.TaskResponse;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskStatus;
import com.justdoit.task.feature.category.Category;
import com.justdoit.task.feature.category.CategoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskService {

    private final TaskRepository taskRepository;
    private final SubTaskRepository subTaskRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public TaskResponse createTask(TaskRequest request, UUID userId) {
        Category category = null;
        if (request.categoryId() != null) {
            category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        }
        Task task = Task.builder()
                .userId(userId)
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
        if (request.categoryId() != null) {
            Category category = categoryRepository.findByIdAndUserId(request.categoryId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Category not found"));
            task.setCategory(category);
        }
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setDueDate(request.dueDate());
        task.setDueTime(request.dueTime());
        if (request.priority() != null) task.setPriority(request.priority());
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public void deleteTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        taskRepository.delete(task);
    }

    public TaskResponse getTaskById(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return toResponse(task);
    }

    public List<TaskResponse> getAllTasksByUser(UUID userId) {
        return taskRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public TaskResponse completeTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        task.setStatus(TaskStatus.COMPLETED);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public TaskResponse reopenTask(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        task.setStatus(TaskStatus.PENDING);
        return toResponse(taskRepository.save(task));
    }

    @Transactional
    public SubTaskResponse addSubTask(UUID taskId, SubTaskRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        SubTask subTask = SubTask.builder()
                .task(task)
                .title(request.title())
                .status(TaskStatus.PENDING)
                .position(request.position())
                .build();
        return toSubTaskResponse(subTaskRepository.save(subTask));
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
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getDueTime(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private SubTaskResponse toSubTaskResponse(SubTask sub) {
        return new SubTaskResponse(sub.getId(), sub.getTitle(), sub.getStatus(), sub.getPosition());
    }
}
