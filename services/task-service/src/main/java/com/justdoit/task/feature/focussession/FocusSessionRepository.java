package com.justdoit.task.feature.focussession;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {
    List<FocusSession> findByTaskId(UUID taskId);
    Optional<FocusSession> findByIdAndTaskId(UUID id, UUID taskId);

    // Sessões do usuário no período — fonte do "tempo executado" no /tasks/report
    // (o TaskTimer é acumulado sem data, não dá para recortar por período).
    List<FocusSession> findByTask_UserIdAndStartedAtBetween(UUID userId,
                                                            java.time.LocalDateTime from,
                                                            java.time.LocalDateTime to);
}
