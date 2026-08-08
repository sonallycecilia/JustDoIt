package com.justdoit.task.feature.timer;

import com.justdoit.task.shared.ActiveTimerResponse;
import com.justdoit.task.shared.TaskTimerLogRequest;
import com.justdoit.task.shared.TaskTimerRequest;
import com.justdoit.task.shared.TaskTimerResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/tasks/{taskId}/timer")
@RequiredArgsConstructor
public class TaskTimerController {

    private final TaskTimerService timerService;

    @GetMapping
    public ResponseEntity<TaskTimerResponse> getTimer(@PathVariable UUID taskId,
                                                       @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.getTimer(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PutMapping
    public ResponseEntity<TaskTimerResponse> upsertTimer(@PathVariable UUID taskId,
                                                          @RequestBody TaskTimerRequest request,
                                                          @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.upsertTimer(taskId, request, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PatchMapping("/log")
    public ResponseEntity<TaskTimerResponse> logSeconds(@PathVariable UUID taskId,
                                                         @RequestBody @Valid TaskTimerLogRequest request,
                                                         @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(somarComRetry(taskId, request.seconds(), request.source(), userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Aciona o cronômetro. Enquanto ele estiver rodando, qualquer outro acionamento do mesmo
     * usuário — outra tarefa, outra aba, duplo clique — recebe 409.
     */
    @PostMapping("/start")
    public ResponseEntity<ActiveTimerResponse> start(@PathVariable UUID taskId,
                                                      @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.start(taskId, userId));
        } catch (CronometroJaAtivoException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /** Para o cronômetro e soma o tempo decorrido, medido pelo servidor. */
    @PostMapping("/stop")
    public ResponseEntity<TaskTimerResponse> stop(@PathVariable UUID taskId,
                                                   @AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.stop(taskId, userId));
        } catch (DataIntegrityViolationException e) {
            return ResponseEntity.ok(timerService.stop(taskId, userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Uma única retentativa para a corrida de criação do timer: dois logs simultâneos numa
     * tarefa que ainda não tinha registro tentam inserir ao mesmo tempo e o unique de
     * {@code task_id} barra o segundo. A retentativa roda em transação nova — a original já
     * sofreu rollback — e desta vez encontra o registro criado pelo concorrente.
     */
    private TaskTimerResponse somarComRetry(UUID taskId, Long seconds,
                                            com.justdoit.task.shared.TimeEntrySource source,
                                            UUID userId) {
        try {
            return source == null
                    ? timerService.logSeconds(taskId, seconds, userId)
                    : timerService.logSeconds(taskId, seconds, source, userId);
        } catch (DataIntegrityViolationException e) {
            return source == null
                    ? timerService.logSeconds(taskId, seconds, userId)
                    : timerService.logSeconds(taskId, seconds, source, userId);
        }
    }
}
