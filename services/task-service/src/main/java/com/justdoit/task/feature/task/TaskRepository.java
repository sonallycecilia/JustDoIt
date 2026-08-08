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

    // Caminhos de leitura que serializam TaskResponse: trazem cycleConfig e timer
    // junto (left join fetch) para expor cycleType e a estimativa sem N+1 nem lazy
    // loading fora da transação. left join = tarefas sem ciclo/cronômetro continuam
    // vindo. As duas associações são to-one, então o fetch múltiplo não duplica linhas.
    @Query("select t from Task t left join fetch t.cycleConfig left join fetch t.timer where t.userId = :userId")
    List<Task> findByUserIdWithCycle(@Param("userId") UUID userId);

    @Query("select t from Task t left join fetch t.cycleConfig left join fetch t.timer "
         + "where t.id = :id and t.userId = :userId")
    Optional<Task> findByIdAndUserIdWithCycle(@Param("id") UUID id, @Param("userId") UUID userId);

    // Exportação de dados (/me/export): traz categoria, cronômetro e nota junto,
    // porque o arquivo precisa de todos e um lazy load por tarefa viraria N+1.
    // As três associações são to-one, então o join fetch múltiplo não duplica linhas.
    @Query("select t from Task t "
         + "left join fetch t.category "
         + "left join fetch t.timer "
         + "left join fetch t.note "
         + "where t.userId = :userId order by t.createdAt")
    List<Task> findByUserIdForExport(@Param("userId") UUID userId);

    // Relatório por período (consumido pelo dashboard e pelo schedule-service via
    // /tasks/report). Traz o cronômetro junto porque o relatório soma a estimativa,
    // que mora em TaskTimer.estimatedMinutes — sem o fetch seria um lazy load por tarefa.
    @Query("select t from Task t left join fetch t.timer left join fetch t.category "
         + "where t.userId = :userId and t.dueDate between :from and :to")
    List<Task> findByUserIdAndDueDateBetweenWithTimer(@Param("userId") UUID userId,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    @Query("select t from Task t left join fetch t.timer left join fetch t.category "
         + "where t.userId = :userId and t.dueDate is null")
    List<Task> findUndatedByUserIdWithTimer(@Param("userId") UUID userId);

    @Query("select count(t) from Task t where t.userId = :userId "
         + "and t.dueDate < :date and t.status <> com.justdoit.task.shared.TaskStatus.COMPLETED")
    long countOverdueOpen(@Param("userId") UUID userId, @Param("date") LocalDate date);

    List<Task> findByUserIdAndCompletedAtBetween(UUID userId, LocalDateTime from, LocalDateTime to);

    // Job de detecção de tarefas atrasadas (todos os usuários)
    List<Task> findByStatusInAndDueDateBefore(Collection<TaskStatus> statuses, LocalDate date);

    // Ciclicidade: ocorrências futuras (a partir de hoje) ainda pendentes de uma série.
    long countBySeriesIdAndStatusAndDueDateGreaterThanEqual(UUID seriesId, TaskStatus status, LocalDate date);
    List<Task> findBySeriesIdAndStatusAndDueDateGreaterThanEqual(UUID seriesId, TaskStatus status, LocalDate date);
    List<Task> findBySeriesIdAndUserId(UUID seriesId, UUID userId);

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
