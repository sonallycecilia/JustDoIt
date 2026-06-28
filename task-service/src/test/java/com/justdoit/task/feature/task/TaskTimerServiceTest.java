package com.justdoit.task.feature.task;

import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskTimerServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskTimerRepository timerRepository;
    @InjectMocks private TaskTimerService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID TIMER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Task task;
    private TaskTimer timer;

    @BeforeEach
    void setUp() {
        task = Task.builder().id(TASK_ID).userId(USER_ID).title("Task").build();
        timer = TaskTimer.builder().id(TIMER_ID).task(task).estimatedMinutes(30).actualSeconds(0L).build();
    }

    @Test
    void getTimer_returnsResponse() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(timer));

        TaskTimerResponse result = service.getTimer(TASK_ID, USER_ID);

        assertEquals(TIMER_ID, result.id());
        assertEquals(TASK_ID, result.taskId());
        assertEquals(30, result.estimatedMinutes());
        assertEquals(0L, result.actualSeconds());
    }

    @Test
    void getTimer_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getTimer(TASK_ID, USER_ID));
    }

    @Test
    void getTimer_whenTimerNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getTimer(TASK_ID, USER_ID));
    }

    @Test
    void upsertTimer_whenTimerAbsent_createsNew() {
        TaskTimerRequest request = new TaskTimerRequest(45, null, null);
        TaskTimer saved = TaskTimer.builder().id(TIMER_ID).task(task).estimatedMinutes(45).actualSeconds(0L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        when(timerRepository.save(any())).thenReturn(saved);

        TaskTimerResponse result = service.upsertTimer(TASK_ID, request, USER_ID);

        assertEquals(45, result.estimatedMinutes());
        verify(timerRepository).save(any(TaskTimer.class));
    }

    @Test
    void upsertTimer_whenTimerPresent_updatesFields() {
        LocalDateTime completedAt = LocalDateTime.now();
        TaskTimerRequest request = new TaskTimerRequest(60, 1800L, completedAt);
        TaskTimer saved = TaskTimer.builder().id(TIMER_ID).task(task)
                .estimatedMinutes(60).actualSeconds(1800L).completedAt(completedAt).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any())).thenReturn(saved);

        TaskTimerResponse result = service.upsertTimer(TASK_ID, request, USER_ID);

        ArgumentCaptor<TaskTimer> captor = ArgumentCaptor.forClass(TaskTimer.class);
        verify(timerRepository).save(captor.capture());
        assertEquals(60, captor.getValue().getEstimatedMinutes());
        assertEquals(1800L, captor.getValue().getActualSeconds());
        assertEquals(1800L, result.actualSeconds());
    }

    @Test
    void logSeconds_addsToActualSeconds() {
        timer.setActualSeconds(100L);
        TaskTimer saved = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(150L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any())).thenReturn(saved);

        TaskTimerResponse result = service.logSeconds(TASK_ID, 50L, USER_ID);

        ArgumentCaptor<TaskTimer> captor = ArgumentCaptor.forClass(TaskTimer.class);
        verify(timerRepository).save(captor.capture());
        assertEquals(150L, captor.getValue().getActualSeconds());
        assertEquals(150L, result.actualSeconds());
    }

    @Test
    void logSeconds_whenTimerNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.logSeconds(TASK_ID, 60L, USER_ID));
    }
}
