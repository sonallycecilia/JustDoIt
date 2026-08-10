// src/main/java/com/justdoit/task/feature/weeklyclosure/domain/WeeklyCycleHistoryService.java
package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class WeeklyCycleHistoryService {

    private final WeeklyCycleRepository cycleRepository;
    private final WeeklyTaskSnapshotRepository taskSnapshotRepository;
    private final WeeklyTimeEntrySnapshotRepository timeSnapshotRepository;

    public WeeklyCycleHistoryService(
            WeeklyCycleRepository cycleRepository,
            WeeklyTaskSnapshotRepository taskSnapshotRepository,
            WeeklyTimeEntrySnapshotRepository timeSnapshotRepository) {
        this.cycleRepository = cycleRepository;
        this.taskSnapshotRepository = taskSnapshotRepository;
        this.timeSnapshotRepository = timeSnapshotRepository;
    }

    public List<WeeklyCycle> getClosedCycles(UUID userId) {
        return cycleRepository.findAllByUserIdAndStatusOrderByStartDateDesc(userId, CycleStatus.CLOSED);
    }

    public CycleHistoryDetailDTO getCycleSnapshots(UUID cycleId, UUID userId) {
        // Valida se o ciclo existe e pertence ao usuário para garantir a segurança
        WeeklyCycle cycle = cycleRepository.findByIdAndUserId(cycleId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Ciclo não encontrado ou acesso negado."));

        List<WeeklyTaskSnapshot> tasks = taskSnapshotRepository.findAllByCycleId(cycleId);
        List<WeeklyTimeEntrySnapshot> times = timeSnapshotRepository.findAllByCycleId(cycleId);

        return new CycleHistoryDetailDTO(cycle, tasks, times);
    }
}