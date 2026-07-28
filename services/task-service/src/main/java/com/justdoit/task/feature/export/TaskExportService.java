package com.justdoit.task.feature.export;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.timer.TaskTimer;
import com.justdoit.task.shared.ExportFormat;
import com.justdoit.task.shared.TaskExportResponse;
import com.justdoit.task.shared.TaskStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Exportação dos dados de produtividade e tarefas do usuário.
 *
 * O corte por dono é feito numa única query por userId (o mesmo userId que o
 * JwtAuthFilter injeta como principal) — não existe parâmetro de usuário vindo
 * do cliente, então não há como pedir a exportação de outra pessoa.
 */
@Service
@RequiredArgsConstructor
public class TaskExportService {

    /** Cabeçalho do CSV: os mesmos nomes e a mesma ordem dos campos de TaskRow. */
    static final List<String> CSV_HEADERS = List.of(
            "id", "title", "status", "completed", "categoryName", "priority",
            "dueDate", "createdAt", "completedAt", "estimatedSeconds", "actualSeconds", "note");

    private static final String CRLF = "\r\n"; // RFC 4180

    // O Excel abre CSV sem BOM na página de código local e estraga acentos —
    // e o app é todo em português. O BOM é ignorado por leitores modernos
    // (pandas, Google Sheets, LibreOffice), então o custo é zero.
    // (escrito por código do caractere: um BOM cru no fonte seria invisível ao ler o arquivo)
    static final String BOM = String.valueOf((char) 0xFEFF);

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public TaskExportResponse export(UUID userId) {
        List<TaskExportResponse.TaskRow> rows = taskRepository.findByUserIdForExport(userId).stream()
                .map(TaskExportService::toRow)
                .toList();
        return new TaskExportResponse(LocalDateTime.now(), userId, rows.size(), rows);
    }

    /**
     * Renderiza o mesmo conteúdo do JSON em CSV. Conta sem tarefas gera um
     * arquivo válido: só a linha de cabeçalho.
     */
    public String toCsv(TaskExportResponse export) {
        StringBuilder csv = new StringBuilder(BOM);
        csv.append(String.join(",", CSV_HEADERS)).append(CRLF);
        for (TaskExportResponse.TaskRow row : export.tasks()) {
            csv.append(campo(row.id())).append(',')
               .append(campo(row.title())).append(',')
               .append(campo(row.status())).append(',')
               .append(campo(row.completed())).append(',')
               .append(campo(row.categoryName())).append(',')
               .append(campo(row.priority())).append(',')
               .append(campo(row.dueDate())).append(',')
               .append(campo(row.createdAt())).append(',')
               .append(campo(row.completedAt())).append(',')
               .append(campo(row.estimatedSeconds())).append(',')
               .append(campo(row.actualSeconds())).append(',')
               .append(campo(row.note()))
               .append(CRLF);
        }
        return csv.toString();
    }

    /** Ex.: export_tarefas_2026-07-27.csv */
    public String fileName(ExportFormat format, LocalDate date) {
        return "export_tarefas_" + date + "." + format.name().toLowerCase(Locale.ROOT);
    }

    private static TaskExportResponse.TaskRow toRow(Task task) {
        TaskTimer timer = task.getTimer();
        // A estimativa que o usuário edita mora no módulo de cronômetro
        // (PUT /tasks/{id}/timer). Task.estimatedMinutes é a coluna antiga, que o
        // TaskService nem escreve mais — fica só como fallback de dados legados.
        Integer estimatedMinutes = timer != null && timer.getEstimatedMinutes() != null
                ? timer.getEstimatedMinutes()
                : task.getEstimatedMinutes();

        return new TaskExportResponse.TaskRow(
                task.getId(),
                task.getTitle(),
                task.getStatus(),
                task.getStatus() == TaskStatus.COMPLETED,
                task.getCategory() != null ? task.getCategory().getName() : null,
                task.getPriority(),
                task.getDueDate(),
                task.getCreatedAt(),
                task.getCompletedAt(),
                estimatedMinutes != null ? estimatedMinutes * 60L : null,
                timer != null && timer.getActualSeconds() != null ? timer.getActualSeconds() : 0L,
                task.getNote() != null ? task.getNote().getContent() : null);
    }

    /**
     * Escapa um campo conforme a RFC 4180: só entra entre aspas quando contém
     * vírgula, aspas ou quebra de linha (caso comum vindo do bloco de notas), e
     * aspas internas viram aspas duplicadas. Nulo vira campo vazio.
     *
     * Não neutralizamos fórmulas de planilha (=, +, @): o arquivo contém apenas o
     * que o próprio usuário digitou e é aberto por ele mesmo, e prefixar os
     * valores corromperia o backup — que é justamente o propósito do arquivo.
     */
    static String campo(Object valor) {
        if (valor == null) return "";
        String texto = String.valueOf(valor);
        if (texto.indexOf('"') >= 0 || texto.indexOf(',') >= 0
                || texto.indexOf('\n') >= 0 || texto.indexOf('\r') >= 0) {
            return '"' + texto.replace("\"", "\"\"") + '"';
        }
        return texto;
    }
}
