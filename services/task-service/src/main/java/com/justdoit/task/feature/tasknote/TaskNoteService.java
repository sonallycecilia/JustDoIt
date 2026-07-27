package com.justdoit.task.feature.tasknote;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.TaskNoteRequest;
import com.justdoit.task.shared.TaskNoteResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TaskNoteService {

    private final TaskRepository taskRepository;
    private final TaskNoteRepository noteRepository;

    public TaskNoteResponse getNote(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskNote note = noteRepository.findByTaskId(taskId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found"));
        return toResponse(note);
    }

    @Transactional
    public TaskNoteResponse upsertNote(UUID taskId, TaskNoteRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        TaskNote note = noteRepository.findByTaskId(taskId)
                .orElse(TaskNote.builder().task(task).build());
        note.setContent(request.content());
        return toResponse(noteRepository.save(note));
    }

    // Limpar a nota é DELETE, e não um PUT com content vazio: o TaskNoteRequest
    // exige @NotBlank, e afrouxar isso tornaria "nota vazia" indistinguível de
    // "sem nota". Idempotente — apagar o que não existe não é erro.
    @Transactional
    public void deleteNote(UUID taskId, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        noteRepository.findByTaskId(taskId).ifPresent(note -> {
            // Task.note é o lado inverso com cascade = ALL. A task acima está
            // gerenciada nesta transação, então se o campo continuar apontando
            // para a nota o flush a RE-PERSISTE e o delete some sem erro algum
            // (a resposta ainda é 204). Quebrar a referência é obrigatório.
            task.setNote(null);
            noteRepository.delete(note);
            noteRepository.flush();
        });
    }

    private TaskNoteResponse toResponse(TaskNote note) {
        return new TaskNoteResponse(
                note.getId(),
                note.getTask().getId(),
                note.getContent(),
                note.getCreatedAt(),
                note.getUpdatedAt()
        );
    }
}
