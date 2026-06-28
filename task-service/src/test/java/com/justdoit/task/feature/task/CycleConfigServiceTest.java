package com.justdoit.task.feature.task;

import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CycleConfigServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private CycleConfigRepository cycleConfigRepository;
    @InjectMocks private CycleConfigService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Task task;
    private CycleConfig config;

    @BeforeEach
    void setUp() {
        task = Task.builder().id(TASK_ID).userId(USER_ID).title("Task").build();
        config = CycleConfig.builder()
                .id(CONFIG_ID).task(task)
                .cycleType(CycleType.WEEKLY)
                .startDate(LocalDate.of(2025, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .build();
    }

    @Test
    void getCycleConfig_returnsResponse() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(cycleConfigRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(config));

        CycleConfigResponse result = service.getCycleConfig(TASK_ID, USER_ID);

        assertEquals(CONFIG_ID, result.id());
        assertEquals(TASK_ID, result.taskId());
        assertEquals(CycleType.WEEKLY, result.cycleType());
        assertEquals(LocalDate.of(2025, 1, 1), result.startDate());
        assertEquals(LocalDate.of(2025, 12, 31), result.endDate());
    }

    @Test
    void getCycleConfig_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getCycleConfig(TASK_ID, USER_ID));
    }

    @Test
    void getCycleConfig_whenConfigNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(cycleConfigRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getCycleConfig(TASK_ID, USER_ID));
    }

    @Test
    void upsertCycleConfig_whenAbsent_createsNew() {
        LocalDate start = LocalDate.of(2025, 6, 1);
        CycleConfigRequest request = new CycleConfigRequest(CycleType.DAILY, start, null, null);
        CycleConfig saved = CycleConfig.builder()
                .id(CONFIG_ID).task(task).cycleType(CycleType.DAILY).startDate(start).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(cycleConfigRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        when(cycleConfigRepository.save(any())).thenReturn(saved);

        CycleConfigResponse result = service.upsertCycleConfig(TASK_ID, request, USER_ID);

        assertEquals(CycleType.DAILY, result.cycleType());
        assertEquals(start, result.startDate());
        verify(cycleConfigRepository).save(any(CycleConfig.class));
    }

    @Test
    void upsertCycleConfig_whenPresent_updatesCycleType() {
        CycleConfigRequest request = new CycleConfigRequest(CycleType.MONTHLY, null, null, null);
        CycleConfig saved = CycleConfig.builder()
                .id(CONFIG_ID).task(task).cycleType(CycleType.MONTHLY).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(cycleConfigRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(config));
        when(cycleConfigRepository.save(any())).thenReturn(saved);

        CycleConfigResponse result = service.upsertCycleConfig(TASK_ID, request, USER_ID);

        ArgumentCaptor<CycleConfig> captor = ArgumentCaptor.forClass(CycleConfig.class);
        verify(cycleConfigRepository).save(captor.capture());
        assertEquals(CycleType.MONTHLY, captor.getValue().getCycleType());
        assertEquals(CycleType.MONTHLY, result.cycleType());
    }

    @Test
    void upsertCycleConfig_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.upsertCycleConfig(TASK_ID, new CycleConfigRequest(CycleType.DAILY, null, null, null), USER_ID));
    }
}
