package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.Task; 
import com.justdoit.task.feature.task.TaskRepository; 
import com.justdoit.task.feature.timer.ActiveTimer;
import com.justdoit.task.feature.timer.ActiveTimerRepository;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.feature.timer.TaskTimerRepository;
import com.justdoit.task.shared.TaskStatus; 
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class WeeklyClosureService {

    private final WeeklyCycleRepository cycleRepository;
    private final WeeklyTaskSnapshotRepository taskSnapshotRepository;
    private final WeeklyTimeEntrySnapshotRepository timeSnapshotRepository;
    private final TaskRepository taskRepository;
    private final TaskTimerRepository taskTimerRepository;
    private final ActiveTimerRepository activeTimerRepository;

    public WeeklyClosureService(
            WeeklyCycleRepository cycleRepository,
            WeeklyTaskSnapshotRepository taskSnapshotRepository,
            WeeklyTimeEntrySnapshotRepository timeSnapshotRepository,
            TaskRepository taskRepository,
            TaskTimerRepository taskTimerRepository,
            ActiveTimerRepository activeTimerRepository) {
        this.cycleRepository = cycleRepository;
        this.taskSnapshotRepository = taskSnapshotRepository;
        this.timeSnapshotRepository = timeSnapshotRepository;
        this.taskRepository = taskRepository;
        this.taskTimerRepository = taskTimerRepository;
        this.activeTimerRepository = activeTimerRepository;
    }

    @Transactional
    public void executeClosure(ClosureCommandDTO command) {
        WeeklyCycle closingCycle = cycleRepository.findByIdAndUserId(command.cycleId(), command.userId())
                .orElseThrow(() -> new IllegalArgumentException("Ciclo não encontrado ou não pertence a este usuário."));

        if (closingCycle.getStatus() == CycleStatus.CLOSED) {
            throw new IllegalStateException("O fechamento já foi processado para este ciclo. Requisição ignorada.");
        }

        List<Task> activeTasks = taskRepository.findAllByCycleId(closingCycle.getId());

        generateTaskSnapshots(activeTasks, closingCycle.getId());
        generateTimeSnapshots(activeTasks, closingCycle.getId());

        WeeklyCycle nextCycle = createNextCycle(closingCycle);

        processTaskMigrationAndArchiving(activeTasks, command, nextCycle.getId());

        closingCycle.closeCycle();
        cycleRepository.save(closingCycle);
    }
    private void generateTaskSnapshots(List<Task> activeTasks, UUID cycleId) {
        List<WeeklyTaskSnapshot> snapshots = new ArrayList<>();
        
        for (Task task : activeTasks) {
            TaskStatusAtClosure statusAtClosure = mapToClosureStatus(task.getStatus());
            Integer points = task.getEstimatedMinutes() != null ? task.getEstimatedMinutes() : 0;
            
            WeeklyTaskSnapshot snapshot = new WeeklyTaskSnapshot(
                    null,
                    cycleId,
                    task.getId(),
                    task.getTitle(),
                    statusAtClosure,
                    points,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            snapshots.add(snapshot);
        }
        
        taskSnapshotRepository.saveAll(snapshots);
    }

    private void generateTimeSnapshots(List<Task> activeTasks, UUID cycleId) {
        List<WeeklyTimeEntrySnapshot> timeSnapshots = new ArrayList<>();
        
        for (Task task : activeTasks) {
            Integer timeLoggedThisWeek = calculateTimeLoggedStrictlyThisWeek(task.getId());
            
            WeeklyTimeEntrySnapshot timeSnapshot = new WeeklyTimeEntrySnapshot(
                    null,
                    cycleId,
                    task.getId(),
                    timeLoggedThisWeek,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            timeSnapshots.add(timeSnapshot);
        }
        
        timeSnapshotRepository.saveAll(timeSnapshots);
    }

    private Integer calculateTimeLoggedStrictlyThisWeek(UUID taskId) {
        long totalAccumulatedSeconds = 0;

        var taskTimerOpt = taskTimerRepository.findByTaskId(taskId);
        if (taskTimerOpt.isPresent() && taskTimerOpt.get().getActualSeconds() != null) {
            totalAccumulatedSeconds += taskTimerOpt.get().getActualSeconds();
        }

        var activeTimerOpt = activeTimerRepository.findByTaskId(taskId);
        if (activeTimerOpt.isPresent()) {
            LocalDateTime startedAt = activeTimerOpt.get().getStartedAt();
            long runningSeconds = Duration.between(startedAt, LocalDateTime.now()).getSeconds();
            totalAccumulatedSeconds += runningSeconds;
            
            taskTimerRepository.incrementActualSeconds(taskId, runningSeconds);
            activeTimerRepository.delete(activeTimerOpt.get());
        }

        int totalAccumulatedMinutes = (int) (totalAccumulatedSeconds / 60);
        int previousWeeksMinutes = timeSnapshotRepository.sumPreviousLoggedMinutesByTaskId(taskId);
        int minutesThisWeek = totalAccumulatedMinutes - previousWeeksMinutes;

        return Math.max(minutesThisWeek, 0);
    }

    private WeeklyCycle createNextCycle(WeeklyCycle closingCycle) {
        LocalDateTime nextStartDate = closingCycle.getEndDate().plusSeconds(1);
        LocalDateTime nextEndDate = nextStartDate.plusDays(7).minusSeconds(1);

        WeeklyCycle nextCycle = new WeeklyCycle(
                null,
                closingCycle.getUserId(),
                nextStartDate,
                nextEndDate,
                CycleStatus.OPEN,
                LocalDateTime.now(),
                LocalDateTime.now()
            );
            
        return cycleRepository.save(nextCycle);
    }

    private void processTaskMigrationAndArchiving(List<Task> activeTasks, ClosureCommandDTO command, UUID nextCycleId) {
        for (Task task : activeTasks) {
            if (command.tasksToMigrate().contains(task.getId())) {
                task.setCycleId(nextCycleId);
            } else if (command.tasksToArchive().contains(task.getId())) {
                task.setStatus(TaskStatus.CANCELLED); 
            }
        }
        taskRepository.saveAll(activeTasks);
    }

    private TaskStatusAtClosure mapToClosureStatus(TaskStatus status) {
        if (status == null) return TaskStatusAtClosure.TODO;
        
        return switch (status) {
            case PENDING -> TaskStatusAtClosure.TODO;
            case IN_PROGRESS -> TaskStatusAtClosure.IN_PROGRESS;
            case COMPLETED -> TaskStatusAtClosure.DONE;
            case CANCELLED -> TaskStatusAtClosure.ARCHIVED; 
            default -> TaskStatusAtClosure.TODO;
        };
    }
}