package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Component
public class CycleMutabilityGuard {

    private final WeeklyCycleRepository cycleRepository;

    public CycleMutabilityGuard(WeeklyCycleRepository cycleRepository) {
        this.cycleRepository = cycleRepository;
    }

    /**
     * Valida se a tarefa pertence a um ciclo fechado. Se pertencer, bloqueia a ação com HTTP 403.
     * Deve ser invocado antes de salvar qualquer atualização, exclusão ou registro de tempo na Task.
     * 
     * @param cycleId O ID do ciclo vinculado à tarefa. Pode ser nulo se a tarefa ainda não tiver ciclo.
     */
    public void ensureTaskIsMutable(UUID cycleId) {
        if (cycleId == null) {
            return; // Tarefas sem ciclo podem ser modificadas livremente
        }

        WeeklyCycle cycle = cycleRepository.findById(cycleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ciclo não encontrado."));

        if (cycle.getStatus() == CycleStatus.CLOSED) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN, 
                    "Ação bloqueada. Esta tarefa pertence a um ciclo semanal já encerrado e seu histórico é imutável."
            );
        }
    }
}