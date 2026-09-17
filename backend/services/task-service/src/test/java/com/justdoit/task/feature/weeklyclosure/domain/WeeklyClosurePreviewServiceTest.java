package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.shared.TaskStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WeeklyClosurePreviewServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private WeeklyCycleProvisioningService provisioningService;
    @Mock private WeeklyCycleRepository cycleRepository;
    @InjectMocks private WeeklyClosurePreviewService service;

    @Test
    void cicloPendenteTemPrecedenciaESuaEstimativaVemDoTimer() {
        UUID userId = UUID.randomUUID();
        UUID cycleId = UUID.randomUUID();
        LocalDateTime start = LocalDateTime.of(2026, 8, 10, 0, 0);
        WeeklyCycle pending = new WeeklyCycle(cycleId, userId, start,
                start.plusDays(7).minusSeconds(1), CycleStatus.PENDING_REVIEW, start, start);
        Task task = Task.builder().id(UUID.randomUUID()).userId(userId).cycleId(cycleId)
                .title("Histórica").status(TaskStatus.PENDING).build();
        task.setTimer(TaskTimer.builder().task(task).estimatedMinutes(90).build());

        when(cycleRepository.findFirstByUserIdAndStatusOrderByStartDateDesc(userId, CycleStatus.PENDING_REVIEW))
                .thenReturn(Optional.of(pending));
        when(taskRepository.findAllByCycleId(cycleId)).thenReturn(List.of(task));

        ClosurePreviewDTO result = service.getClosurePreview(userId);

        assertEquals(start.toLocalDate(), result.weekStartDate());
        assertEquals(start.plusDays(6).toLocalDate(), result.weekEndDate());
        assertEquals(90, result.pendingTasks().get(0).estimatedMinutes());
        verifyNoInteractions(provisioningService);
    }
}
