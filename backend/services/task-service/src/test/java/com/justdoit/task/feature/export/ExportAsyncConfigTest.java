package com.justdoit.task.feature.export;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import static org.assertj.core.api.Assertions.assertThat;

class ExportAsyncConfigTest {

    @Test
    @DisplayName("executor respeita concorrência e fila configuradas")
    void executorHasHardLimits() {
        ExportProperties properties = new ExportProperties();
        properties.setMaxConcurrency(2);
        properties.setQueueCapacity(3);
        SimpleMeterRegistry registry = new SimpleMeterRegistry();

        ThreadPoolTaskExecutor executor = (ThreadPoolTaskExecutor)
                new ExportAsyncConfig().exportExecutor(properties, registry);
        try {
            assertThat(executor.getCorePoolSize()).isEqualTo(2);
            assertThat(executor.getMaxPoolSize()).isEqualTo(2);
            assertThat(executor.getThreadPoolExecutor().getQueue().remainingCapacity()).isEqualTo(3);
            assertThat(registry.get("justdoit.export.executor.active").gauge()).isNotNull();
            assertThat(registry.get("justdoit.export.queue.size").gauge()).isNotNull();
        } finally {
            executor.shutdown();
        }
    }
}
