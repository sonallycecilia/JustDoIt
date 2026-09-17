package com.justdoit.task.feature.export;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskExecutor;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
@EnableConfigurationProperties(ExportProperties.class)
public class ExportAsyncConfig {

    @Bean("exportExecutor")
    public TaskExecutor exportExecutor(ExportProperties properties, MeterRegistry registry) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.getMaxConcurrency());
        executor.setMaxPoolSize(properties.getMaxConcurrency());
        executor.setQueueCapacity(properties.getQueueCapacity());
        executor.setThreadNamePrefix("task-export-");
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        Gauge.builder("justdoit.export.executor.active", executor,
                        ThreadPoolTaskExecutor::getActiveCount)
                .description("Threads do pool exclusivo de exportação em uso")
                .register(registry);
        Gauge.builder("justdoit.export.queue.size", executor,
                        value -> value.getThreadPoolExecutor().getQueue().size())
                .description("Jobs aguardando no pool exclusivo de exportação")
                .register(registry);
        return executor;
    }
}
