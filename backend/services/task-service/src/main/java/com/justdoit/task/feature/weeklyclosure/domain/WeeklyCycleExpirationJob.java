package com.justdoit.task.feature.weeklyclosure.domain;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class WeeklyCycleExpirationJob {

    private final WeeklyCycleRepository cycleRepository;

    public WeeklyCycleExpirationJob(WeeklyCycleRepository cycleRepository) {
        this.cycleRepository = cycleRepository;
    }

    // A expressão Cron "0 0 * * * *" executa a varredura a cada hora cravada.
    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireOpenCycles() {
        LocalDateTime now = LocalDateTime.now();
        
        // Busca todos os ciclos que estão ABERTOS mas cuja data de fim já passou
        List<WeeklyCycle> expiredCycles = cycleRepository.findByStatusAndEndDateBefore(CycleStatus.OPEN, now);

        for (WeeklyCycle cycle : expiredCycles) {
            // Muda o status para PENDING_REVIEW (O que forçará o modal de triagem no frontend)
            cycle.pendReview();
        }

        if (!expiredCycles.isEmpty()) {
            cycleRepository.saveAll(expiredCycles);
        }
    }
}