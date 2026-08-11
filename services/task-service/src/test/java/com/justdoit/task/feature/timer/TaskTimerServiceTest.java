package com.justdoit.task.feature.timer;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.weeklyclosure.domain.CycleMutabilityGuard;

import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskTimerServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskTimerRepository timerRepository;
    @Mock private ActiveTimerRepository activeTimerRepository;
    @Mock private TimeEntryRepository timeEntryRepository;
    @Mock private CycleMutabilityGuard cycleMutabilityGuard;
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
        // A soma é feita pelo banco, num UPDATE atômico — não lendo e reescrevendo o valor.
        TaskTimer atualizado = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(150L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.incrementActualSeconds(TASK_ID, 50L)).thenReturn(1);
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(atualizado));

        TaskTimerResponse result = service.logSeconds(TASK_ID, 50L, USER_ID);

        verify(timerRepository).incrementActualSeconds(TASK_ID, 50L);
        verify(timerRepository, never()).save(any());
        assertEquals(150L, result.actualSeconds());
    }

    @Test
    void logSeconds_whenTimerAbsent_createsTimerWithLoggedSeconds() {
        // logSeconds é upsert: quando o UPDATE não encontra linha (0 afetadas), o primeiro
        // log da tarefa cria o timer já com os segundos, sem exigir PUT prévio.
        TaskTimer criado = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(60L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.incrementActualSeconds(TASK_ID, 60L)).thenReturn(0);
        when(taskRepository.getReferenceById(TASK_ID)).thenReturn(task);
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(criado));

        TaskTimerResponse result = service.logSeconds(TASK_ID, 60L, USER_ID);

        ArgumentCaptor<TaskTimer> captor = ArgumentCaptor.forClass(TaskTimer.class);
        verify(timerRepository).save(captor.capture());
        assertEquals(60L, captor.getValue().getActualSeconds());
        assertEquals(60L, result.actualSeconds());
    }

    @Test
    void logSeconds_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.logSeconds(TASK_ID, 60L, USER_ID));
    }

    // ─────────────────────────────────────────────
    // start / stop — um cronômetro ativo por usuário
    // ─────────────────────────────────────────────

    @Test
    void start_whenSemCronometroAtivo_acionaCronometro() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(activeTimerRepository.saveAndFlush(any())).thenAnswer(i -> i.getArgument(0));

        ActiveTimerResponse result = service.start(TASK_ID, USER_ID);

        ArgumentCaptor<ActiveTimer> captor = ArgumentCaptor.forClass(ActiveTimer.class);
        verify(activeTimerRepository).saveAndFlush(captor.capture());
        assertEquals(USER_ID, captor.getValue().getUserId());
        assertEquals(TASK_ID, result.taskId());
        assertNotNull(result.startedAt());
    }

    @Test
    void start_whenJaExisteCronometroAtivo_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(ativo(TASK_ID, LocalDateTime.now())));

        assertThrows(CronometroJaAtivoException.class, () -> service.start(TASK_ID, USER_ID));
        verify(activeTimerRepository, never()).saveAndFlush(any());
    }

    @Test
    void start_whenPerdeCorridaNoInsert_throwsException() {
        // Acionamento simultâneo: a verificação prévia passou, mas o índice único barrou o
        // insert. É este caminho que sustenta a métrica de cronômetro concorrente.
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        when(activeTimerRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("unique active_timer.user_id"));

        assertThrows(CronometroJaAtivoException.class, () -> service.start(TASK_ID, USER_ID));
    }

    @Test
    void start_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.start(TASK_ID, USER_ID));
    }

    @Test
    void stop_somaTempoDecorridoEEncerraCronometro() {
        ActiveTimer ativo = ativo(TASK_ID, LocalDateTime.now().minusMinutes(2));
        TaskTimer atualizado = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(120L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(ativo));
        when(timerRepository.incrementActualSeconds(eq(TASK_ID), anyLong())).thenReturn(1);
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(atualizado));

        TaskTimerResponse result = service.stop(TASK_ID, USER_ID);

        verify(activeTimerRepository).delete(ativo);
        verify(timerRepository).incrementActualSeconds(TASK_ID, 120L);
        assertEquals(120L, result.actualSeconds());
    }

    @Test
    void stop_gravaIntervaloDatadoComOInicioDoServidor() {
        // Sem esta linha o tempo do cronômetro fica invisível para qualquer relatório
        // por período: TaskTimer.actualSeconds é um acumulado sem data.
        LocalDateTime inicio = LocalDateTime.now().minusMinutes(2);
        ActiveTimer ativo = ativo(TASK_ID, inicio);
        TaskTimer atualizado = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(120L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(ativo));
        when(timerRepository.incrementActualSeconds(eq(TASK_ID), anyLong())).thenReturn(1);
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(atualizado));

        service.stop(TASK_ID, USER_ID);

        ArgumentCaptor<TimeEntry> captor = ArgumentCaptor.forClass(TimeEntry.class);
        verify(timeEntryRepository).save(captor.capture());
        TimeEntry gravado = captor.getValue();
        assertEquals(TASK_ID, gravado.getTask().getId());
        assertEquals(inicio, gravado.getStartedAt()); // o início é o do servidor, não "agora menos X"
        assertEquals(120L, gravado.getSeconds());
        assertNotNull(gravado.getEndedAt());
    }

    @Test
    void stop_semTempoDecorrido_naoGravaIntervaloVazio() {
        ActiveTimer ativo = ativo(TASK_ID, LocalDateTime.now());
        TaskTimer atualizado = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(0L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.of(ativo));
        when(timerRepository.incrementActualSeconds(eq(TASK_ID), anyLong())).thenReturn(1);
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(atualizado));

        service.stop(TASK_ID, USER_ID);

        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    void logSeconds_gravaIntervaloTerminandoAgora() {
        // O log manual informa só a duração, então o intervalo é ancorado no fim.
        TaskTimer atualizado = TaskTimer.builder().id(TIMER_ID).task(task).actualSeconds(150L).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.incrementActualSeconds(TASK_ID, 50L)).thenReturn(1);
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(atualizado));

        service.logSeconds(TASK_ID, 50L, USER_ID);

        ArgumentCaptor<TimeEntry> captor = ArgumentCaptor.forClass(TimeEntry.class);
        verify(timeEntryRepository).save(captor.capture());
        assertEquals(50L, captor.getValue().getSeconds());
        assertEquals(50L, java.time.Duration.between(
                captor.getValue().getStartedAt(), captor.getValue().getEndedAt()).getSeconds());
    }

    @Test
    void upsertTimer_zerandoOCronometro_apagaOHistoricoDatado() {
        // Zerar tem de valer nas duas fontes, senão o acumulado (0) e o relatório
        // passariam a contar coisas diferentes.
        TaskTimerRequest request = new TaskTimerRequest(null, 0L, null);
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertTimer(TASK_ID, request, USER_ID);

        verify(timeEntryRepository).deleteByTaskId(TASK_ID);
        verify(timeEntryRepository, never()).save(any()); // zerado não vira intervalo de 0s
    }

    @Test
    void upsertTimer_definindoTotal_reescreveOHistoricoParaBater() {
        // Caminho da tarefa NOVA: o cronômetro rodou antes de a tarefa existir e o
        // total é gravado de uma vez. O intervalo é reescrito para casar com ele.
        TaskTimerRequest request = new TaskTimerRequest(60, 1800L, null);
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertTimer(TASK_ID, request, USER_ID);

        ArgumentCaptor<TimeEntry> captor = ArgumentCaptor.forClass(TimeEntry.class);
        verify(timeEntryRepository).deleteByTaskId(TASK_ID);
        verify(timeEntryRepository).save(captor.capture());
        assertEquals(1800L, captor.getValue().getSeconds());
    }

    @Test
    void upsertTimer_semActualSeconds_naoMexeNoHistorico() {
        // Salvar só a estimativa não pode apagar tempo trabalhado.
        TaskTimerRequest request = new TaskTimerRequest(45, null, null);
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(timerRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(timer));
        when(timerRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        service.upsertTimer(TASK_ID, request, USER_ID);

        verify(timeEntryRepository, never()).deleteByTaskId(any());
        verify(timeEntryRepository, never()).save(any());
    }

    @Test
    void stop_whenSemCronometroAtivo_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.stop(TASK_ID, USER_ID));
    }

    @Test
    void stop_whenCronometroEDeOutraTarefa_throwsException() {
        UUID outraTarefa = UUID.fromString("00000000-0000-0000-0000-000000000009");
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(activeTimerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(ativo(outraTarefa, LocalDateTime.now())));

        assertThrows(IllegalArgumentException.class, () -> service.stop(TASK_ID, USER_ID));
        verify(timerRepository, never()).incrementActualSeconds(any(), anyLong());
    }

    @Test
    void getActive_returnsCronometroEmCurso() {
        when(activeTimerRepository.findByUserId(USER_ID))
                .thenReturn(Optional.of(ativo(TASK_ID, LocalDateTime.now())));

        assertEquals(TASK_ID, service.getActive(USER_ID).taskId());
    }

    @Test
    void getActive_whenSemCronometroAtivo_throwsException() {
        when(activeTimerRepository.findByUserId(USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getActive(USER_ID));
    }

    private ActiveTimer ativo(UUID taskId, LocalDateTime startedAt) {
        return ActiveTimer.builder().id(UUID.randomUUID())
                .userId(USER_ID).taskId(taskId).startedAt(startedAt).build();
    }
}
