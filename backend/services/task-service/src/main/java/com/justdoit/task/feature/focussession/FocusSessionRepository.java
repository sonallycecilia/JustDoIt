package com.justdoit.task.feature.focussession;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {
    List<FocusSession> findByTaskId(UUID taskId);
    Optional<FocusSession> findByIdAndTaskId(UUID id, UUID taskId);

    // Sessões do usuário no período — fonte do "tempo executado" no /tasks/report
    // (o TaskTimer é acumulado sem data, não dá para recortar por período).
    @EntityGraph(attributePaths = {"task", "task.category", "task.timer"})
    List<FocusSession> findByTask_UserIdAndStartedAtBetween(UUID userId,
                                                            java.time.LocalDateTime from,
                                                            java.time.LocalDateTime to);

    List<FocusSession> findByTaskIdAndStartedAtBetween(UUID taskId,
                                                       java.time.LocalDateTime from,
                                                       java.time.LocalDateTime to);
}
