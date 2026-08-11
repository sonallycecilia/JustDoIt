package com.justdoit.task.feature.export;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.time.Duration;

@ConfigurationProperties(prefix = "app.export")
public class ExportProperties {
    private Path storagePath = Path.of("data", "exports");
    private int pageSize = 200;
    private int maxConcurrency = 2;
    private int queueCapacity = 8;
    private int maxActivePerUser = 1;
    private long maxRecords = 1_000_000;
    private long maxFileSizeBytes = 536_870_912;
    private Duration maxDuration = Duration.ofMinutes(30);
    private Duration downloadTtl = Duration.ofMinutes(15);
    private String publicApiBaseUrl = "http://localhost:8081";

    public Path getStoragePath() { return storagePath; }
    public void setStoragePath(Path storagePath) { this.storagePath = storagePath; }
    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) { this.pageSize = pageSize; }
    public int getMaxConcurrency() { return maxConcurrency; }
    public void setMaxConcurrency(int maxConcurrency) { this.maxConcurrency = maxConcurrency; }
    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }
    public int getMaxActivePerUser() { return maxActivePerUser; }
    public void setMaxActivePerUser(int maxActivePerUser) { this.maxActivePerUser = maxActivePerUser; }
    public long getMaxRecords() { return maxRecords; }
    public void setMaxRecords(long maxRecords) { this.maxRecords = maxRecords; }
    public long getMaxFileSizeBytes() { return maxFileSizeBytes; }
    public void setMaxFileSizeBytes(long maxFileSizeBytes) { this.maxFileSizeBytes = maxFileSizeBytes; }
    public Duration getMaxDuration() { return maxDuration; }
    public void setMaxDuration(Duration maxDuration) { this.maxDuration = maxDuration; }
    public Duration getDownloadTtl() { return downloadTtl; }
    public void setDownloadTtl(Duration downloadTtl) { this.downloadTtl = downloadTtl; }
    public String getPublicApiBaseUrl() { return publicApiBaseUrl; }
    public void setPublicApiBaseUrl(String publicApiBaseUrl) { this.publicApiBaseUrl = publicApiBaseUrl; }
}
