package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.Task; 
import com.justdoit.task.feature.task.TaskRepository; 
import com.justdoit.task.feature.task.TaskEstimates;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.timer.ActiveTimer;
import com.justdoit.task.feature.timer.ActiveTimerRepository;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.feature.timer.TaskTimerRepository;
import com.justdoit.task.feature.timer.TimeEntry;
import com.justdoit.task.feature.timer.TimeEntryRepository;
import com.justdoit.task.shared.TimeEntrySource;
import com.justdoit.task.shared.SessionType;
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
    private final TimeEntryRepository timeEntryRepository;
    private final FocusSessionRepository focusSessionRepository;

    public WeeklyClosureService(
            WeeklyCycleRepository cycleRepository,
            WeeklyTaskSnapshotRepository taskSnapshotRepository,
            WeeklyTimeEntrySnapshotRepository timeSnapshotRepository,
            TaskRepository taskRepository,
            TaskTimerRepository taskTimerRepository,
            ActiveTimerRepository activeTimerRepository,
            TimeEntryRepository timeEntryRepository,
            FocusSessionRepository focusSessionRepository) {
        this.cycleRepository = cycleRepository;
        this.taskSnapshotRepository = taskSnapshotRepository;
        this.timeSnapshotRepository = timeSnapshotRepository;
        this.taskRepository = taskRepository;
        this.taskTimerRepository = taskTimerRepository;
        this.activeTimerRepository = activeTimerRepository;
        this.timeEntryRepository = timeEntryRepository;
        this.focusSessionRepository = focusSessionRepository;
    }

    @Transactional
    public void executeClosure(ClosureCommandDTO command) {
        WeeklyCycle closingCycle = cycleRepository.findByIdAndUserId(command.cycleId(), command.userId())
                .orElseThrow(() -> new IllegalArgumentException("Ciclo não encontrado ou não pertence a este usuário."));

        if (closingCycle.getStatus() == CycleStatus.CLOSED) {
            // Idempotência permite repetir a orquestração caso o snapshot do
            // schedule-service tenha falhado depois deste fechamento concluir.
            return;
        }

        List<Task> activeTasks = taskRepository.findAllByCycleId(closingCycle.getId());

        generateTaskSnapshots(activeTasks, closingCycle.getId());
        generateTimeSnapshots(activeTasks, closingCycle);

        WeeklyCycle nextCycle = createNextCycle(closingCycle);

        processTaskMigrationAndArchiving(activeTasks, command, nextCycle.getId());

        closingCycle.closeCycle();
        cycleRepository.save(closingCycle);
    }
    private void generateTaskSnapshots(List<Task> activeTasks, UUID cycleId) {
        List<WeeklyTaskSnapshot> snapshots = new ArrayList<>();
        
        for (Task task : activeTasks) {
            TaskStatusAtClosure statusAtClosure = mapToClosureStatus(task.getStatus());
            Integer points = TaskEstimates.minutesOrZero(task);
            
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

    private void generateTimeSnapshots(List<Task> activeTasks, WeeklyCycle cycle) {
        List<WeeklyTimeEntrySnapshot> timeSnapshots = new ArrayList<>();
        
        for (Task task : activeTasks) {
            Integer timeLoggedThisWeek = calculateTimeLoggedStrictlyThisWeek(
                    task.getId(), cycle.getStartDate(), cycle.getEndDate());
            
            WeeklyTimeEntrySnapshot timeSnapshot = new WeeklyTimeEntrySnapshot(
                    null,
                    cycle.getId(),
                    task.getId(),
                    timeLoggedThisWeek,
                    LocalDateTime.now(),
                    LocalDateTime.now()
            );
            timeSnapshots.add(timeSnapshot);
        }
        
        timeSnapshotRepository.saveAll(timeSnapshots);
    }

    private Integer calculateTimeLoggedStrictlyThisWeek(UUID taskId,
                                                        LocalDateTime weekStart,
                                                        LocalDateTime weekEnd) {
        var activeTimerOpt = activeTimerRepository.findByTaskId(taskId);
        if (activeTimerOpt.isPresent()) {
            LocalDateTime startedAt = activeTimerOpt.get().getStartedAt();
            LocalDateTime endedAt = LocalDateTime.now();
            long runningSeconds = Duration.between(startedAt, endedAt).getSeconds();
            if (runningSeconds > 0) {
                taskTimerRepository.incrementActualSeconds(taskId, runningSeconds);
                // O relatório histórico recorta TimeEntry por data; atualizar apenas
                // o acumulado deixava o cronômetro ativo invisível no snapshot.
                timeEntryRepository.save(TimeEntry.builder()
                        .task(taskRepository.getReferenceById(taskId))
                        .startedAt(startedAt)
                        .endedAt(endedAt)
                        .seconds(runningSeconds)
                        .source(TimeEntrySource.TIMER)
                        .build());
            }
            activeTimerRepository.delete(activeTimerOpt.get());
        }

        long timerSeconds = timeEntryRepository
                .findByTaskIdAndStartedAtBetween(taskId, weekStart, weekEnd).stream()
                .mapToLong(TimeEntry::getSeconds)
                .filter(seconds -> seconds > 0)
                .sum();
        long focusSeconds = focusSessionRepository
                .findByTaskIdAndStartedAtBetween(taskId, weekStart, weekEnd).stream()
                .filter(session -> session.getSessionType() != SessionType.BREAK)
                .mapToLong(WeeklyClosureService::focusSeconds)
                .sum();
        return (int) Math.max(0, (timerSeconds + focusSeconds) / 60);
    }

    private static long focusSeconds(FocusSession session) {
        if (session.getStartedAt() != null && session.getEndedAt() != null
                && session.getEndedAt().isAfter(session.getStartedAt())) {
            return Duration.between(session.getStartedAt(), session.getEndedAt()).getSeconds();
        }
        if (Boolean.TRUE.equals(session.getCompleted()) && session.getFocusMinutes() != null) {
            return session.getFocusMinutes() * 60L;
        }
        return 0;
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
