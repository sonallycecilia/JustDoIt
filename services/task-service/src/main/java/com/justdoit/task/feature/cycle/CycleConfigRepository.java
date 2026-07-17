package com.justdoit.task.feature.cycle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CycleConfigRepository extends JpaRepository<CycleConfig, UUID> {
    Optional<CycleConfig> findByTaskId(UUID taskId);

    // Todas as séries com a task-modelo carregada (fetch) — o job varre todas e
    // deixa o materializer decidir (limitado por contagem) se há o que gerar.
    @Query("select c from CycleConfig c join fetch c.task")
    List<CycleConfig> findAllWithTask();
}
