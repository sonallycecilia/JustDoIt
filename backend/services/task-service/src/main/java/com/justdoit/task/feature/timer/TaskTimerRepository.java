package com.justdoit.task.feature.timer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface TaskTimerRepository extends JpaRepository<TaskTimer, UUID> {
    Optional<TaskTimer> findByTaskId(UUID taskId);

    /**
     * Soma segundos ao acumulado em um único UPDATE, deixando a aritmética com o banco.
     *
     * <p>Substitui o padrão anterior (ler o timer, somar em memória, salvar): dois logs
     * concorrentes liam o mesmo valor e o último save vencia, descartando o tempo do outro
     * — perda silenciosa, exatamente o tipo de erro que corrompe as métricas do usuário.
     *
     * @return quantas linhas foram atualizadas: 0 quando a tarefa ainda não tem timer.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update TaskTimer t set t.actualSeconds = t.actualSeconds + :seconds where t.task.id = :taskId")
    int incrementActualSeconds(@Param("taskId") UUID taskId, @Param("seconds") long seconds);
}
