package com.justdoit.task.feature.cycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mantém as ocorrências futuras das tarefas cíclicas materializadas dentro de uma
 * janela deslizante. A materialização acontece na CRIAÇÃO da recorrência
 * ({@link CycleConfigService}); este job apenas ROLA a janela conforme os dias
 * passam — todo dia gera as novas ocorrências que entraram no horizonte.
 *
 * A lógica de geração/idempotência/encerramento por endDate vive no
 * {@link CycleMaterializer} (compartilhado com a criação).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CycleInstanceJob {

    private final CycleConfigRepository cycleConfigRepository;
    private final CycleMaterializer materializer;

    @Scheduled(cron = "0 30 0 * * *") // diariamente, 00:30
    @Transactional
    public void generateDueCycleInstances() {
        // Repõe as ocorrências futuras de cada série conforme as antigas vão passando.
        // O materializer limita por quantidade, então varrer todas é seguro (no-op
        // nas que já estão cheias).
        int total = 0;
        for (CycleConfig config : cycleConfigRepository.findAllWithTask()) {
            total += materializer.materialize(config);
        }
        if (total > 0) {
            log.info("{} ocorrência(s) cíclica(s) materializada(s)", total);
        }
    }
}
