package com.justdoit.task.feature.timer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {

    // Intervalos do usuário no período — fonte do tempo de cronômetro no
    // /tasks/report. Mesmo formato do FocusSessionRepository (navega por task.userId).
    @EntityGraph(attributePaths = {"task", "task.category", "task.timer"})
    List<TimeEntry> findByTask_UserIdAndStartedAtBetween(UUID userId,
                                                         LocalDateTime from,
                                                         LocalDateTime to);

    // Zerar o cronômetro da tarefa apaga também o histórico datado, senão o
    // acumulado (0) e o relatório passariam a contar coisas diferentes.
    void deleteByTaskId(UUID taskId);
}
