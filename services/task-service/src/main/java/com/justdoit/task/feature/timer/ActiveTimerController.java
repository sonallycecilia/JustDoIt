package com.justdoit.task.feature.timer;

import com.justdoit.task.shared.ActiveTimerResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Consulta do cronômetro em curso, sem depender de saber a tarefa.
 *
 * <p>É o caminho de volta de quem fecha o navegador com o cronômetro rodando: sem isto, o
 * usuário levaria 409 em toda tarefa que tentasse acionar, sem descobrir qual está travando.
 */
@RestController
@RequestMapping("/timers")
@RequiredArgsConstructor
public class ActiveTimerController {

    private final TaskTimerService timerService;

    @GetMapping("/active")
    public ResponseEntity<ActiveTimerResponse> getActive(@AuthenticationPrincipal UUID userId) {
        try {
            return ResponseEntity.ok(timerService.getActive(userId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
