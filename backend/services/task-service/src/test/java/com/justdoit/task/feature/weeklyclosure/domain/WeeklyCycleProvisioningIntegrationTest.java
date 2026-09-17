package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.TaskService;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskRequest;
import com.justdoit.task.shared.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
class WeeklyCycleProvisioningIntegrationTest {

    @Autowired private TaskService taskService;
    @Autowired private TaskRepository taskRepository;
    @Autowired private WeeklyCycleRepository cycleRepository;

    @Test
    void primeiraTarefaDoUsuarioPersisteOCicloAntesDeReferenciarSeuId() {
        UUID userId = UUID.randomUUID();
        TaskRequest request = new TaskRequest(
                "Tarefa recorrente",
                null,
                null,
                null,
                Priority.NORMAL,
                LocalDate.now(),
                null,
                null
        );

        TaskResponse created = taskService.createTask(request, userId);
        Task task = taskRepository.findById(created.id()).orElseThrow();

        assertTrue(cycleRepository.existsById(task.getCycleId()),
                "task.cycle_id deve apontar para um weekly_cycles persistido");
    }
}
