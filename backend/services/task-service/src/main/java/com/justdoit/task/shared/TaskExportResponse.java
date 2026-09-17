package com.justdoit.task.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Exportação completa das tarefas do usuário (portabilidade / backup local).
 * É um envelope, e não uma lista crua, para que o arquivo baixado carregue
 * também quando foi gerado e de quem é — informação que se perde num array solto.
 *
 * Convenções do arquivo (valem igualmente para JSON e CSV):
 * - datas em ISO 8601 (LocalDate "2026-07-27", LocalDateTime "2026-07-27T14:30:00");
 * - tempo SEMPRE em segundos, nos dois campos, para não misturar unidades
 *   (o front guarda estimativa em minutos e cronômetro em segundos);
 * - o CSV usa exatamente estes nomes de campo como cabeçalho, na mesma ordem
 *   dos componentes de TaskRow.
 */
public record TaskExportResponse(
        LocalDateTime exportedAt,
        UUID userId,
        int taskCount,
        List<TaskRow> tasks
) {
    /**
     * Uma tarefa do usuário com os módulos que fazem sentido num backup:
     * categoria vinculada, cronômetro (estimativa + tempo real) e bloco de notas.
     *
     * A categoria entra pelo NOME, não pelo id: o arquivo é lido por gente e em
     * planilha, onde um UUID de categoria não diz nada (o id da própria tarefa
     * fica porque é o que permite casar a linha com o registro no sistema).
     */
    public record TaskRow(
            UUID id,
            String title,
            TaskStatus status,
            boolean completed,
            String categoryName,
            Priority priority,
            LocalDate dueDate,
            LocalDateTime createdAt,
            LocalDateTime completedAt,
            Long estimatedSeconds,
            Long actualSeconds,
            String note
    ) { }
}
