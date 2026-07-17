package com.justdoit.task.feature.timer;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.TaskTimerRequest;
import com.justdoit.task.shared.TaskTimerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskTimerService {

    private final TaskRepository taskRepository;
    private final TaskTimerRepository timerRepository;

    public TaskTimerResponse getTimer(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskTimer timer = timerRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Timer not found"));
        return toResponse(timer);
    }

    @Transactional
    public TaskTimerResponse upsertTimer(UUID taskId, TaskTimerRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskTimer timer = timerRepository.findByTaskId(taskId)
                .orElse(TaskTimer.builder().task(task).build());
        if (request.estimatedMinutes() != null) timer.setEstimatedMinutes(request.estimatedMinutes());
        if (request.actualSeconds() != null) timer.setActualSeconds(request.actualSeconds());
        if (request.completedAt() != null) timer.setCompletedAt(request.completedAt());
        return toResponse(timerRepository.save(timer));
    }

    @Transactional
    public TaskTimerResponse logSeconds(UUID taskId, Long seconds, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        // Upsert: o primeiro log de tempo de uma tarefa cria o timer, em vez de
        // exigir um PUT prévio (o front loga direto ao pausar o cronômetro).
        TaskTimer timer = timerRepository.findByTaskId(taskId)
                .orElse(TaskTimer.builder().task(task).build());
        timer.setActualSeconds(timer.getActualSeconds() + seconds);
        return toResponse(timerRepository.save(timer));
    }

    private TaskTimerResponse toResponse(TaskTimer timer) {
        return new TaskTimerResponse(
                timer.getId(),
                timer.getTask().getId(),
                timer.getEstimatedMinutes(),
                timer.getActualSeconds(),
                timer.getCompletedAt()
        );
    }
}
