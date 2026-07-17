package com.justdoit.task.feature.moduleconfig;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskModuleConfigServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskModuleConfigRepository configRepository;
    @InjectMocks private TaskModuleConfigService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CONFIG_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Task task;

    @BeforeEach
    void setUp() {
        task = Task.builder().id(TASK_ID).userId(USER_ID).title("Task").build();
    }

    @Test
    void getConfig_returnsResponse() {
        TaskModuleConfig config = TaskModuleConfig.builder()
                .id(CONFIG_ID).task(task)
                .focusEnabled(true).cycleEnabled(false)
                .priorityEnabled(true).timerEnabled(false).notesEnabled(true)
                .build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(configRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(config));

        TaskModuleConfigResponse result = service.getConfig(TASK_ID, USER_ID);

        assertEquals(CONFIG_ID, result.id());
        assertEquals(TASK_ID, result.taskId());
        assertTrue(result.focusEnabled());
        assertFalse(result.cycleEnabled());
        assertTrue(result.notesEnabled());
    }

    @Test
    void getConfig_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getConfig(TASK_ID, USER_ID));
    }

    @Test
    void getConfig_whenConfigNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(configRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getConfig(TASK_ID, USER_ID));
    }

    @Test
    void upsertConfig_whenConfigAbsent_createsNew() {
        TaskModuleConfigRequest request = new TaskModuleConfigRequest(true, true, null, null, null);
        TaskModuleConfig saved = TaskModuleConfig.builder()
                .id(CONFIG_ID).task(task).focusEnabled(true).cycleEnabled(true).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(configRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        when(configRepository.save(any())).thenReturn(saved);

        TaskModuleConfigResponse result = service.upsertConfig(TASK_ID, request, USER_ID);

        assertEquals(CONFIG_ID, result.id());
        assertTrue(result.focusEnabled());
        verify(configRepository).save(any(TaskModuleConfig.class));
    }

    @Test
    void upsertConfig_whenConfigPresent_updatesFields() {
        TaskModuleConfigRequest request = new TaskModuleConfigRequest(true, null, null, true, null);
        TaskModuleConfig existing = TaskModuleConfig.builder()
                .id(CONFIG_ID).task(task).build();
        TaskModuleConfig saved = TaskModuleConfig.builder()
                .id(CONFIG_ID).task(task).focusEnabled(true).timerEnabled(true).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(configRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(existing));
        when(configRepository.save(any())).thenReturn(saved);

        TaskModuleConfigResponse result = service.upsertConfig(TASK_ID, request, USER_ID);

        ArgumentCaptor<TaskModuleConfig> captor = ArgumentCaptor.forClass(TaskModuleConfig.class);
        verify(configRepository).save(captor.capture());
        assertTrue(captor.getValue().getFocusEnabled());
        assertTrue(captor.getValue().getTimerEnabled());
        assertTrue(result.focusEnabled());
    }

    @Test
    void upsertConfig_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.upsertConfig(TASK_ID, new TaskModuleConfigRequest(null, null, null, null, null), USER_ID));
    }
}
