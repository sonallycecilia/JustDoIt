package com.justdoit.task.feature.export;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class ExportMetrics {

    private final MeterRegistry registry;
    private final Timer duration;
    private final DistributionSummary records;
    private final DistributionSummary fileBytes;
    private final AtomicInteger active = new AtomicInteger();

    public ExportMetrics(MeterRegistry registry) {
        this.registry = registry;
        this.duration = Timer.builder("justdoit.export.duration")
                .description("Duração da geração assíncrona")
                .publishPercentileHistogram()
                .register(registry);
        this.records = DistributionSummary.builder("justdoit.export.records")
                .baseUnit("records").register(registry);
        this.fileBytes = DistributionSummary.builder("justdoit.export.file.size")
                .baseUnit("bytes").register(registry);
        Gauge.builder("justdoit.export.active", active, AtomicInteger::get)
                .description("Workers de exportação ativos").register(registry);
    }

    public void accepted() { counter("accepted").increment(); }
    public void rejected() { counter("rejected").increment(); }
    public void started() { active.incrementAndGet(); }

    public void completed(Duration elapsed, ExportGenerationResult result) {
        active.decrementAndGet();
        duration.record(elapsed);
        records.record(result.recordCount());
        fileBytes.record(result.fileSizeBytes());
        counter("completed").increment();
    }

    public void failed(Duration elapsed, String reason) {
        active.decrementAndGet();
        duration.record(elapsed);
        Counter.builder("justdoit.export.jobs")
                .tag("status", "failed")
                .tag("reason", reason)
                .register(registry).increment();
    }

    private Counter counter(String status) {
        return Counter.builder("justdoit.export.jobs")
                .tag("status", status).register(registry);
    }
}
