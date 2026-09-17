package com.justdoit.task.feature.export;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Abre uma transação curta por página. Ao retornar, entidades e associações
 * saem do contexto de persistência e podem ser coletadas antes da próxima página.
 */
@Component
@RequiredArgsConstructor
public class TaskExportPageReader {

    private final TaskRepository taskRepository;

    @Transactional(readOnly = true)
    public ExportPage read(UUID userId, int pageNumber, int pageSize) {
        var pageable = PageRequest.of(pageNumber, pageSize,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("id")));
        var slice = taskRepository.findSliceByUserId(userId, pageable);
        List<com.justdoit.task.shared.TaskExportResponse.TaskRow> rows = slice.getContent().stream()
                .map(TaskExportService::toRow)
                .toList();
        return new ExportPage(rows, slice.hasNext());
    }

    @Transactional(readOnly = true)
    public long count(UUID userId) {
        return taskRepository.countByUserId(userId);
    }
}
