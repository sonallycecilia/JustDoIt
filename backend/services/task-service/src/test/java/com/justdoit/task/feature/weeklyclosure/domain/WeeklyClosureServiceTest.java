package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.focussession.FocusSessionRepository;
import com.justdoit.task.feature.focussession.FocusSession;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.timer.*;
import com.justdoit.task.shared.TaskStatus;
import com.justdoit.task.shared.SessionType;
import com.justdoit.task.shared.TimeEntrySource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyClosureServiceTest {

    @Mock private WeeklyCycleRepository cycleRepository;
    @Mock private WeeklyTaskSnapshotRepository taskSnapshotRepository;
    @Mock private WeeklyTimeEntrySnapshotRepository timeSnapshotRepository;
    @Mock private TaskRepository taskRepository;
    @Mock private TaskTimerRepository taskTimerRepository;
    @Mock private ActiveTimerRepository activeTimerRepository;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private FocusSessionRepository focusSessionRepository;
    @InjectMocks private WeeklyClosureService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CYCLE_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final LocalDateTime START = LocalDateTime.of(2026, 8, 10, 0, 0);

    @Test
    void fechamentoJaConcluidoEhIdempotente() {
        WeeklyCycle closed = cycle(CycleStatus.CLOSED);
        when(cycleRepository.findByIdAndUserId(CYCLE_ID, USER_ID)).thenReturn(Optional.of(closed));

        service.executeClosure(command());

        verifyNoInteractions(taskSnapshotRepository, timeSnapshotRepository, taskRepository);
    }

    @Test
    void snapshotUsaEstimativaRealDoTimer() {
        WeeklyCycle open = cycle(CycleStatus.OPEN);
        Task task = Task.builder().id(TASK_ID).userId(USER_ID).cycleId(CYCLE_ID)
                .title("Planejar").status(TaskStatus.PENDING).estimatedMinutes(null).build();
        task.setTimer(TaskTimer.builder().task(task).estimatedMinutes(120).actualSeconds(0L).build());

        when(cycleRepository.findByIdAndUserId(CYCLE_ID, USER_ID)).thenReturn(Optional.of(open));
        when(taskRepository.findAllByCycleId(CYCLE_ID)).thenReturn(List.of(task));
        when(activeTimerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        when(timeEntryRepository.findByTaskIdAndStartedAtBetween(TASK_ID, open.getStartDate(), open.getEndDate()))
                .thenReturn(List.of());
        when(focusSessionRepository.findByTaskIdAndStartedAtBetween(TASK_ID, open.getStartDate(), open.getEndDate()))
                .thenReturn(List.of());
        when(cycleRepository.save(any(WeeklyCycle.class))).thenAnswer(invocation -> {
            WeeklyCycle saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });

        service.executeClosure(command());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WeeklyTaskSnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(taskSnapshotRepository).saveAll(captor.capture());
        assertEquals(120, captor.getValue().get(0).getPoints());
    }

    @Test
    void snapshotDeTempoSomaCronometroEPomodoroDoPeriodo() {
        WeeklyCycle open = cycle(CycleStatus.OPEN);
        Task task = Task.builder().id(TASK_ID).userId(USER_ID).cycleId(CYCLE_ID)
                .title("Executar").status(TaskStatus.COMPLETED).build();
        TimeEntry timer = TimeEntry.builder().task(task).startedAt(START.plusHours(9))
                .endedAt(START.plusHours(9).plusMinutes(30)).seconds(1_800)
                .source(TimeEntrySource.TIMER).build();
        FocusSession focus = FocusSession.builder().task(task).sessionType(SessionType.FOCUS)
                .startedAt(START.plusHours(10)).endedAt(START.plusHours(10).plusMinutes(25))
                .completed(true).focusMinutes(25).build();

        when(cycleRepository.findByIdAndUserId(CYCLE_ID, USER_ID)).thenReturn(Optional.of(open));
        when(taskRepository.findAllByCycleId(CYCLE_ID)).thenReturn(List.of(task));
        when(activeTimerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        when(timeEntryRepository.findByTaskIdAndStartedAtBetween(TASK_ID, open.getStartDate(), open.getEndDate()))
                .thenReturn(List.of(timer));
        when(focusSessionRepository.findByTaskIdAndStartedAtBetween(TASK_ID, open.getStartDate(), open.getEndDate()))
                .thenReturn(List.of(focus));
        when(cycleRepository.save(any(WeeklyCycle.class))).thenAnswer(invocation -> {
            WeeklyCycle saved = invocation.getArgument(0);
            if (saved.getId() == null) saved.setId(UUID.randomUUID());
            return saved;
        });

        service.executeClosure(command());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<WeeklyTimeEntrySnapshot>> captor = ArgumentCaptor.forClass(List.class);
        verify(timeSnapshotRepository).saveAll(captor.capture());
        assertEquals(55, captor.getValue().get(0).getTimeLoggedMinutes());
    }

    private WeeklyCycle cycle(CycleStatus status) {
        return new WeeklyCycle(CYCLE_ID, USER_ID, START, START.plusDays(7).minusSeconds(1),
                status, START, START);
    }

    private ClosureCommandDTO command() {
        return new ClosureCommandDTO(CYCLE_ID, USER_ID, List.of(TASK_ID), List.of());
    }
}
