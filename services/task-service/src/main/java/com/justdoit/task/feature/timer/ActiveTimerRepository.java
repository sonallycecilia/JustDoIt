package com.justdoit.task.feature.timer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ActiveTimerRepository extends JpaRepository<ActiveTimer, UUID> {

    Optional<ActiveTimer> findByUserId(UUID userId);

    /**
     * Quantos cronômetros ativos o usuário tem. Só pode ser 0 ou 1 — existe para que o teste
     * da métrica verifique isso no banco em vez de confiar na constraint.
     */
    long countByUserId(UUID userId);
}
