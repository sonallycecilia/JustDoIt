package com.justdoit.task.feature.task;

import com.justdoit.task.shared.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
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

    List<Task> findAllByCycleId(UUID cycleId);

    @Query("select t from Task t left join fetch t.cycleConfig left join fetch t.timer where t.userId = :userId")
    List<Task> findByUserIdWithCycle(@Param("userId") UUID userId);

    @Query("select t from Task t left join fetch t.cycleConfig left join fetch t.timer "
         + "where t.id = :id and t.userId = :userId")
    Optional<Task> findByIdAndUserIdWithCycle(@Param("id") UUID id, @Param("userId") UUID userId);

    @Query("select t from Task t "
         + "left join fetch t.category "
         + "left join fetch t.timer "
         + "left join fetch t.note "
         + "where t.userId = :userId order by t.createdAt")
    List<Task> findByUserIdForExport(@Param("userId") UUID userId);

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

    List<Task> findByStatusInAndDueDateBefore(Collection<TaskStatus> statuses, LocalDate date);

    long countBySeriesIdAndStatusAndDueDateGreaterThanEqual(UUID seriesId, TaskStatus status, LocalDate date);
    
    List<Task> findBySeriesIdAndStatusAndDueDateGreaterThanEqual(UUID seriesId, TaskStatus status, LocalDate date);
    
    List<Task> findBySeriesIdAndUserId(UUID seriesId, UUID userId);

    @Query("select max(t.dueDate) from Task t where t.seriesId = :seriesId")
    LocalDate findMaxDueDateBySeriesId(@Param("seriesId") UUID seriesId);

    @Query("select count(t) > 0 from Task t where t.seriesId = :seriesId and t.dueDate = :dueDate " +
           "and ((:dueTime is null and t.dueTime is null) or t.dueTime = :dueTime)")
    boolean existsOccurrence(@Param("seriesId") UUID seriesId,
                             @Param("dueDate") LocalDate dueDate,
                             @Param("dueTime") LocalTime dueTime);

    // Tarefas criadas ANTES da correção do Encerramento Semanal ficaram com
    // cycle_id NULL para sempre — nunca entravam em nenhuma prévia/fechamento
    // porque tudo ali filtra por cycle_id. Esse UPDATE em lote "adota" essas
    // tarefas legadas para o ciclo atualmente aberto do usuário, uma única
    // vez (idempotente: depois da primeira execução não sobra nenhuma com
    // cycle_id nulo, então as próximas chamadas não afetam nenhuma linha).
    @Modifying(clearAutomatically = true)
    @Query("update Task t set t.cycleId = :cycleId where t.userId = :userId and t.cycleId is null")
    int adoptOrphanTasks(@Param("userId") UUID userId, @Param("cycleId") UUID cycleId);
}