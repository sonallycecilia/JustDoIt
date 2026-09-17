package com.justdoit.task.feature.export;

import com.justdoit.task.feature.category.Category;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.tasknote.TaskNote;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.Priority;
import com.justdoit.task.shared.TaskExportResponse;
import com.justdoit.task.shared.TaskStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskExportServiceTest {

    @Mock private TaskRepository taskRepository;
    @InjectMocks private TaskExportService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final LocalDate HOJE = LocalDate.of(2026, 7, 27);

    private Task tarefa(String titulo) {
        return Task.builder()
                .id(UUID.fromString("11111111-1111-1111-1111-111111111111"))
                .userId(USER_ID)
                .title(titulo)
                .status(TaskStatus.PENDING)
                .priority(Priority.NORMAL)
                .createdAt(LocalDateTime.of(2026, 7, 20, 9, 0))
                .build();
    }

    /* ─── AC3: conteúdo ──────────────────────────────────────────────────── */

    @Test
    @DisplayName("exporta todos os campos essenciais da tarefa, com tempo em segundos")
    void export_trazCamposEssenciais() {
        Task task = tarefa("Estudar Spring");
        task.setStatus(TaskStatus.COMPLETED);
        task.setPriority(Priority.URGENT_IMPORTANT);
        task.setDueDate(HOJE);
        task.setCompletedAt(LocalDateTime.of(2026, 7, 26, 18, 30));
        task.setCategory(Category.builder()
                .id(UUID.fromString("22222222-2222-2222-2222-222222222222"))
                .userId(USER_ID).name("Faculdade").color("#f00").build());
        task.setTimer(TaskTimer.builder().estimatedMinutes(90).actualSeconds(5400L).build());
        task.setNote(TaskNote.builder().content("Revisar o capítulo 4").build());
        when(taskRepository.findByUserIdForExport(USER_ID)).thenReturn(List.of(task));

        TaskExportResponse export = service.export(USER_ID);

        assertEquals(USER_ID, export.userId());
        assertEquals(1, export.taskCount());
        TaskExportResponse.TaskRow row = export.tasks().get(0);
        assertEquals("Estudar Spring", row.title());
        assertEquals(TaskStatus.COMPLETED, row.status());
        assertTrue(row.completed());
        assertEquals("Faculdade", row.categoryName());
        assertEquals(LocalDateTime.of(2026, 7, 20, 9, 0), row.createdAt());
        assertEquals(LocalDateTime.of(2026, 7, 26, 18, 30), row.completedAt());
        assertEquals(90 * 60L, row.estimatedSeconds()); // minutos do timer → segundos
        assertEquals(5400L, row.actualSeconds());
        assertEquals("Revisar o capítulo 4", row.note());
    }

    @Test
    @DisplayName("tarefa sem categoria, sem cronômetro e sem nota exporta sem quebrar")
    void export_modulosAusentes_naoQuebram() {
        when(taskRepository.findByUserIdForExport(USER_ID)).thenReturn(List.of(tarefa("Solta")));

        TaskExportResponse.TaskRow row = service.export(USER_ID).tasks().get(0);

        assertNull(row.categoryName());
        assertNull(row.completedAt());
        assertNull(row.estimatedSeconds());
        assertEquals(0L, row.actualSeconds()); // sem cronômetro = nenhum tempo executado
        assertNull(row.note());
        assertFalse(row.completed());
    }

    @Test
    @DisplayName("estimativa cai para Task.estimatedMinutes quando o cronômetro não tem a dela")
    void export_estimativaTemFallbackNaTarefa() {
        Task task = tarefa("Legado");
        task.setEstimatedMinutes(30);
        task.setTimer(TaskTimer.builder().actualSeconds(120L).build());
        when(taskRepository.findByUserIdForExport(USER_ID)).thenReturn(List.of(task));

        assertEquals(30 * 60L, service.export(USER_ID).tasks().get(0).estimatedSeconds());
    }

    /* ─── CSV ────────────────────────────────────────────────────────────── */

    @Test
    @DisplayName("CSV começa com BOM + cabeçalho e usa CRLF")
    void toCsv_cabecalhoEQuebras() {
        when(taskRepository.findByUserIdForExport(USER_ID)).thenReturn(List.of(tarefa("Simples")));

        String csv = service.toCsv(service.export(USER_ID));

        assertTrue(csv.startsWith(TaskExportService.BOM + String.join(",", TaskExportService.CSV_HEADERS) + "\r\n"));
        assertTrue(csv.endsWith("\r\n"));
        assertEquals(2, csv.split("\r\n").length); // cabeçalho + 1 tarefa
    }

    @Test
    @DisplayName("CSV escapa vírgula, aspas e quebra de linha vindas das notas")
    void toCsv_escapaCaracteresEspeciais() {
        Task task = tarefa("Comprar pão, leite e café");
        task.setNote(TaskNote.builder().content("Ele disse \"agora\"\nna segunda linha").build());
        when(taskRepository.findByUserIdForExport(USER_ID)).thenReturn(List.of(task));

        String csv = service.toCsv(service.export(USER_ID));

        assertTrue(csv.contains("\"Comprar pão, leite e café\""));
        assertTrue(csv.contains("\"Ele disse \"\"agora\"\"\nna segunda linha\""));
        // A quebra de linha da nota está dentro de aspas: continua sendo 1 registro.
        assertEquals(1, csv.lines().filter(l -> l.startsWith("11111111-")).count());
    }

    @Test
    @DisplayName("conta sem tarefas gera CSV válido, só com o cabeçalho")
    void toCsv_semTarefas_soCabecalho() {
        when(taskRepository.findByUserIdForExport(USER_ID)).thenReturn(List.of());

        TaskExportResponse export = service.export(USER_ID);

        assertEquals(0, export.taskCount());
        assertTrue(export.tasks().isEmpty());
        assertEquals(TaskExportService.BOM + String.join(",", TaskExportService.CSV_HEADERS) + "\r\n",
                service.toCsv(export));
    }

    /* ─── Nome do arquivo e formato ──────────────────────────────────────── */

    @Test
    @DisplayName("nome do arquivo carimba a data e a extensão do formato")
    void fileName_comTimestamp() {
        assertEquals("export_tarefas_2026-07-27.csv", service.fileName(ExportFormat.CSV, HOJE));
        assertEquals("export_tarefas_2026-07-27.json", service.fileName(ExportFormat.JSON, HOJE));
    }

    @Test
    @DisplayName("formato aceita caixa alta/baixa, cai em JSON quando ausente e rejeita o resto")
    void exportFormat_parse() {
        assertEquals(ExportFormat.CSV, ExportFormat.from("csv"));
        assertEquals(ExportFormat.CSV, ExportFormat.from("CSV"));
        assertEquals(ExportFormat.JSON, ExportFormat.from(null));
        assertEquals(ExportFormat.JSON, ExportFormat.from("  "));
        assertThrows(IllegalArgumentException.class, () -> ExportFormat.from("xlsx"));
    }
}
