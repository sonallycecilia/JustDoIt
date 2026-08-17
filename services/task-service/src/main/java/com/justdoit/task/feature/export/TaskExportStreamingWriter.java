package com.justdoit.task.feature.export;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.TaskExportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/** Escreve diretamente no arquivo; nunca mantém o relatório completo no heap. */
@Component
@RequiredArgsConstructor
public class TaskExportStreamingWriter {

    private static final String CRLF = "\r\n";

    private final TaskExportPageReader pageReader;
    private final ObjectMapper objectMapper;
    private final ExportProperties properties;

    public ExportGenerationResult write(UUID userId, ExportFormat format, Path target) throws IOException {
        long expectedRecords = pageReader.count(userId);
        if (expectedRecords > properties.getMaxRecords()) {
            throw new ExportLimitExceededException(
                    "Exportação possui " + expectedRecords + " registros; limite: "
                            + properties.getMaxRecords());
        }

        Instant deadline = Instant.now().plus(properties.getMaxDuration());
        try (OutputStream file = Files.newOutputStream(target);
             BufferedOutputStream buffered = new BufferedOutputStream(file, 64 * 1024);
             LimitedOutputStream limited = new LimitedOutputStream(
                     buffered, properties.getMaxFileSizeBytes())) {
            long records = format == ExportFormat.CSV
                    ? writeCsv(userId, limited, deadline)
                    : writeJson(userId, limited, deadline);
            limited.flush();
            return new ExportGenerationResult(records, limited.count());
        }
    }

    private long writeCsv(UUID userId, OutputStream output, Instant deadline) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        writer.write(TaskExportService.BOM);
        writer.write(String.join(",", TaskExportService.CSV_HEADERS));
        writer.write(CRLF);
        long records = forEachPage(userId, deadline, row -> {
            writer.write(csvLine(row));
            writer.write(CRLF);
        });
        writer.flush();
        return records;
    }

    private long writeJson(UUID userId, OutputStream output, Instant deadline) throws IOException {
        JsonGenerator generator = objectMapper.getFactory().createGenerator(output);
        generator.writeStartObject();
        generator.writeStringField("exportedAt", LocalDateTime.now().toString());
        generator.writeStringField("userId", userId.toString());
        generator.writeArrayFieldStart("tasks");
        long records = forEachPage(userId, deadline,
                row -> objectMapper.writeValue(generator, row));
        generator.writeEndArray();
        // O total real fica depois do array para continuar 100% streaming e não
        // depender de o conjunto permanecer imutável entre COUNT e paginação.
        generator.writeNumberField("taskCount", records);
        generator.writeEndObject();
        generator.flush();
        return records;
    }

    private long forEachPage(UUID userId, Instant deadline, RowConsumer consumer) throws IOException {
        int pageNumber = 0;
        long records = 0;
        boolean hasNext;
        do {
            checkDeadline(deadline);
            ExportPage page = pageReader.read(userId, pageNumber, properties.getPageSize());
            for (TaskExportResponse.TaskRow row : page.rows()) {
                checkDeadline(deadline);
                consumer.accept(row);
                records++;
            }
            hasNext = page.hasNext();
            pageNumber++;
        } while (hasNext);
        return records;
    }

    private void checkDeadline(Instant deadline) {
        if (Instant.now().isAfter(deadline)) {
            throw new ExportTimeoutException(
                    "Exportação excedeu o timeout de " + properties.getMaxDuration());
        }
    }

    private static String csvLine(TaskExportResponse.TaskRow row) {
        return TaskExportService.campo(row.id()) + ','
                + TaskExportService.campo(row.title()) + ','
                + TaskExportService.campo(row.status()) + ','
                + TaskExportService.campo(row.completed()) + ','
                + TaskExportService.campo(row.categoryName()) + ','
                + TaskExportService.campo(row.priority()) + ','
                + TaskExportService.campo(row.dueDate()) + ','
                + TaskExportService.campo(row.createdAt()) + ','
                + TaskExportService.campo(row.completedAt()) + ','
                + TaskExportService.campo(row.estimatedSeconds()) + ','
                + TaskExportService.campo(row.actualSeconds()) + ','
                + TaskExportService.campo(row.note());
    }

    @FunctionalInterface
    private interface RowConsumer {
        void accept(TaskExportResponse.TaskRow row) throws IOException;
    }
}
