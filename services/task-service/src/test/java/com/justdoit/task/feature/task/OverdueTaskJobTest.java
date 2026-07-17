package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OverdueTaskJobTest {

    @Mock private TaskRepository taskRepository;
    @Mock private NotificationClient notificationClient;
    @InjectMocks private OverdueTaskJob job;

    private Task tarefaVencida(UUID userId) {
        return Task.builder().id(UUID.randomUUID()).userId(userId).title("Atrasada")
                .status(TaskStatus.PENDING).dueDate(LocalDate.now().minusDays(1)).build();
    }

    @Test
    @DisplayName("marca tarefas vencidas como OVERDUE e notifica cada usuário")
    void job_marcaOverdue_eNotifica() {
        UUID user1 = UUID.randomUUID();
        UUID user2 = UUID.randomUUID();
        Task t1 = tarefaVencida(user1);
        Task t2 = tarefaVencida(user2);
        when(taskRepository.findByStatusInAndDueDateBefore(anyCollection(), any(LocalDate.class)))
                .thenReturn(List.of(t1, t2));

        job.markOverdueTasks();

        assertEquals(TaskStatus.OVERDUE, t1.getStatus());
        assertEquals(TaskStatus.OVERDUE, t2.getStatus());
        verify(taskRepository).saveAll(List.of(t1, t2));
        verify(notificationClient).notifyTaskOverdue(user1, t1.getId(), "Atrasada");
        verify(notificationClient).notifyTaskOverdue(user2, t2.getId(), "Atrasada");
    }

    @Test
    @DisplayName("sem tarefas vencidas: não salva nem notifica")
    void job_semVencidas_naoFazNada() {
        when(taskRepository.findByStatusInAndDueDateBefore(anyCollection(), any(LocalDate.class)))
                .thenReturn(List.of());

        job.markOverdueTasks();

        verify(taskRepository, never()).saveAll(any());
        verifyNoInteractions(notificationClient);
    }
}
