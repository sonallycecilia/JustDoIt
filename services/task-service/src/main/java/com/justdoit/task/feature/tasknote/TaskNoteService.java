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
