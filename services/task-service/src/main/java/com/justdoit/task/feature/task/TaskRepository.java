package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TaskRepository extends JpaRepository<Task, UUID> {
    List<Task> findByUserId(UUID userId);
    Optional<Task> findByIdAndUserId(UUID id, UUID userId);
    List<Task> findByCategoryIdAndUserId(UUID categoryId, UUID userId);

    // Caminhos de leitura que serializam TaskResponse: trazem o cycleConfig junto
    // (left join fetch) para expor cycleType sem N+1 nem lazy loading fora da
    // transação. left join = tarefas sem ciclo continuam vindo (cycleType null).
    @Query("select t from Task t left join fetch t.cycleConfig where t.userId = :userId")
    List<Task> findByUserIdWithCycle(@Param("userId") UUID userId);

    @Query("select t from Task t left join fetch t.cycleConfig where t.id = :id and t.userId = :userId")
    Optional<Task> findByIdAndUserIdWithCycle(@Param("id") UUID id, @Param("userId") UUID userId);

    // Relatório por período (consumido pelo schedule-service via /tasks/report)
    long countByUserIdAndDueDateBetween(UUID userId, LocalDate from, LocalDate to);
    List<Task> findByUserIdAndCompletedAtBetween(UUID userId, LocalDateTime from, LocalDateTime to);

    // Job de detecção de tarefas atrasadas (todos os usuários)
    List<Task> findByStatusInAndDueDateBefore(Collection<TaskStatus> statuses, LocalDate date);

    // Ciclicidade: ocorrências futuras (a partir de hoje) ainda pendentes de uma série.
    long countBySeriesIdAndStatusAndDueDateGreaterThanEqual(UUID seriesId, TaskStatus status, LocalDate date);
    List<Task> findBySeriesIdAndStatusAndDueDateGreaterThanEqual(UUID seriesId, TaskStatus status, LocalDate date);

    @Query("select max(t.dueDate) from Task t where t.seriesId = :seriesId")
    LocalDate findMaxDueDateBySeriesId(@Param("seriesId") UUID seriesId);

    // Ciclo CUSTOM: dedup por (série, data, hora). Trata dueTime nula (ocorrências
    // em granularidade de dia) sem gerar "= null" (que nunca casa em SQL).
    @Query("select count(t) > 0 from Task t where t.seriesId = :seriesId and t.dueDate = :dueDate " +
           "and ((:dueTime is null and t.dueTime is null) or t.dueTime = :dueTime)")
    boolean existsOccurrence(@Param("seriesId") UUID seriesId,
                             @Param("dueDate") LocalDate dueDate,
                             @Param("dueTime") LocalTime dueTime);
}
