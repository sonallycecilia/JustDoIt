package com.justdoit.task.feature.export;

import com.justdoit.task.shared.ExportFormat;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ExportFileStorage {

    private final ExportProperties properties;
    private Path root;

    @PostConstruct
    void initialize() throws IOException {
        root = properties.getStoragePath().toAbsolutePath().normalize();
        Files.createDirectories(root);
    }

    public StoredExport allocate(UUID jobId, ExportFormat format) throws IOException {
        String extension = format.name().toLowerCase(Locale.ROOT);
        String key = jobId + "." + extension;
        Path path = resolve(key);
        Files.deleteIfExists(path);
        return new StoredExport(key, path);
    }

    public Resource resource(String storageKey) {
        Path path = resolve(storageKey);
        if (!Files.isRegularFile(path)) {
            throw new IllegalStateException("Arquivo de exportação indisponível");
        }
        return new FileSystemResource(path);
    }

    public void delete(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return;
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
            // A limpeza será repetida pelo job agendado; não mascara o status.
        }
    }

    private Path resolve(String storageKey) {
        Path path = root.resolve(storageKey).normalize();
        if (!path.startsWith(root)) {
            throw new IllegalArgumentException("Chave de storage inválida");
        }
        return path;
    }

    public record StoredExport(String key, Path path) { }
}
