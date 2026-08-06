package com.justdoit.task.feature.timer;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.ActiveTimerResponse;
import com.justdoit.task.shared.TaskTimerRequest;
import com.justdoit.task.shared.TaskTimerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskTimerService {

    private final TaskRepository taskRepository;
    private final TaskTimerRepository timerRepository;
    private final ActiveTimerRepository activeTimerRepository;
    private final TimeEntryRepository timeEntryRepository;

    public TaskTimerResponse getTimer(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskTimer timer = timerRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Timer not found"));
        return toResponse(timer);
    }

    @Transactional
    public TaskTimerResponse upsertTimer(UUID taskId, TaskTimerRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskTimer timer = timerRepository.findByTaskId(taskId)
                .orElse(TaskTimer.builder().task(task).build());
        if (request.estimatedMinutes() != null) timer.setEstimatedMinutes(request.estimatedMinutes());
        if (request.actualSeconds() != null) {
            timer.setActualSeconds(request.actualSeconds());
            // Este PUT DEFINE o total (não incrementa): é o "zerar cronômetro" e a
            // gravação do tempo rodado antes de a tarefa existir. Os intervalos
            // datados são reescritos para bater com o novo total, senão o
            // acumulado e o /tasks/report contariam coisas diferentes.
            timeEntryRepository.deleteByTaskId(taskId);
            if (request.actualSeconds() > 0) {
                registrarIntervalo(task, request.actualSeconds());
            }
        }
        if (request.completedAt() != null) timer.setCompletedAt(request.completedAt());
        return toResponse(timerRepository.save(timer));
    }

    @Transactional
    public TaskTimerResponse logSeconds(UUID taskId, Long seconds, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        if (seconds != null && seconds > 0) {
            registrarIntervalo(task, seconds);
        }
        return somarSegundos(taskId, seconds);
    }

    // ─────────────────────────────────────────────
    // Cronômetro: start / stop
    // ─────────────────────────────────────────────

    /**
     * Aciona o cronômetro da tarefa.
     *
     * @throws CronometroJaAtivoException se o usuário já estiver cronometrando alguma tarefa
     */
    @Transactional
    public ActiveTimerResponse start(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));

        // Atalho para o caso comum (o usuário simplesmente já tem um cronômetro rodando).
        // NÃO é a garantia: entre este if e o insert cabe outra thread. Quem assegura a
        // exclusividade sob concorrência é o índice único em active_timer.user_id.
        if (activeTimerRepository.findByUserId(userId).isPresent()) {
            throw new CronometroJaAtivoException();
        }

        try {
            ActiveTimer ativo = activeTimerRepository.saveAndFlush(ActiveTimer.builder()
                    .userId(userId)
                    .taskId(taskId)
                    .startedAt(LocalDateTime.now())
                    .build());
            return toResponse(ativo);
        } catch (DataIntegrityViolationException e) {
            // Perdeu a corrida: outro acionamento simultâneo inseriu a linha deste usuário
            // primeiro. saveAndFlush (e não save) para que a violação apareça aqui, e não
            // no commit, onde já estaria fora deste try. A transação sofre rollback, o que
            // é inofensivo: este insert é a única escrita da operação.
            throw new CronometroJaAtivoException();
        }
    }

    /** Para o cronômetro e soma ao acumulado o tempo decorrido desde o start. */
    @Transactional
    public TaskTimerResponse stop(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        ActiveTimer ativo = activeTimerRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("No active timer"));
        if (!ativo.getTaskId().equals(taskId)) {
            throw new IllegalArgumentException("Active timer belongs to another task");
        }

        LocalDateTime fim = LocalDateTime.now();
        long decorridos = Math.max(0, Duration.between(ativo.getStartedAt(), fim).getSeconds());
        activeTimerRepository.delete(ativo);
        // Aqui as datas são reais (o início veio do servidor, no start), então o
        // intervalo cai no dia certo mesmo se atravessar a madrugada.
        if (decorridos > 0) {
            timeEntryRepository.save(TimeEntry.builder()
                    .task(task)
                    .startedAt(ativo.getStartedAt())
                    .endedAt(fim)
                    .seconds(decorridos)
                    .build());
        }
        return somarSegundos(taskId, decorridos);
    }

    public ActiveTimerResponse getActive(UUID userId) {
        return activeTimerRepository.findByUserId(userId)
                .map(this::toResponse)
                .orElseThrow(() -> new IllegalArgumentException("No active timer"));
    }

    // ─────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────

    /**
     * Soma segundos ao acumulado da tarefa. O UPDATE é atômico; só quando a tarefa ainda
     * não tem timer é que o registro é criado — o primeiro log de tempo dispensa PUT prévio
     * (o front loga direto ao pausar o cronômetro).
     */
    private TaskTimerResponse somarSegundos(UUID taskId, long seconds) {
        if (timerRepository.incrementActualSeconds(taskId, seconds) == 0) {
            // getReferenceById porque o UPDATE acima limpa o contexto de persistência.
            timerRepository.save(TaskTimer.builder()
                    .task(taskRepository.getReferenceById(taskId))
                    .actualSeconds(seconds)
                    .build());
        }
        return toResponse(timerRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Timer not found")));
    }

    /**
     * Intervalo sem par de datas exato (log manual e gravação do tempo rodado
     * antes de a tarefa existir): assume que os segundos terminaram agora.
     */
    private void registrarIntervalo(Task task, long seconds) {
        LocalDateTime fim = LocalDateTime.now();
        timeEntryRepository.save(TimeEntry.builder()
                .task(task)
                .startedAt(fim.minusSeconds(seconds))
                .endedAt(fim)
                .seconds(seconds)
                .build());
    }

    private TaskTimerResponse toResponse(TaskTimer timer) {
        return new TaskTimerResponse(
                timer.getId(),
                timer.getTask().getId(),
                timer.getEstimatedMinutes(),
                timer.getActualSeconds(),
                timer.getCompletedAt()
        );
    }

    private ActiveTimerResponse toResponse(ActiveTimer ativo) {
        return new ActiveTimerResponse(ativo.getId(), ativo.getTaskId(), ativo.getStartedAt());
    }
}
