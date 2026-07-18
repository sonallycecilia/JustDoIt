package com.justdoit.task.feature.tasknote;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskNoteServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private TaskNoteRepository noteRepository;
    @InjectMocks private TaskNoteService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID NOTE_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Task task;
    private TaskNote note;

    @BeforeEach
    void setUp() {
        task = Task.builder().id(TASK_ID).userId(USER_ID).title("Task").build();
        note = TaskNote.builder().id(NOTE_ID).task(task).content("Original note")
                .createdAt(LocalDateTime.now()).updatedAt(LocalDateTime.now()).build();
    }

    @Test
    void getNote_returnsResponse() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(noteRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(note));

        TaskNoteResponse result = service.getNote(TASK_ID, USER_ID);

        assertEquals(NOTE_ID, result.id());
        assertEquals(TASK_ID, result.taskId());
        assertEquals("Original note", result.content());
        assertNotNull(result.createdAt());
    }

    @Test
    void getNote_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getNote(TASK_ID, USER_ID));
    }

    @Test
    void getNote_whenNoteNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(noteRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.getNote(TASK_ID, USER_ID));
    }

    @Test
    void upsertNote_whenNoteAbsent_createsNew() {
        TaskNoteRequest request = new TaskNoteRequest("New note content");
        TaskNote saved = TaskNote.builder().id(NOTE_ID).task(task).content("New note content").build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(noteRepository.findByTaskId(TASK_ID)).thenReturn(Optional.empty());
        when(noteRepository.save(any())).thenReturn(saved);

        TaskNoteResponse result = service.upsertNote(TASK_ID, request, USER_ID);

        assertEquals("New note content", result.content());
        verify(noteRepository).save(any(TaskNote.class));
    }

    @Test
    void upsertNote_whenNotePresent_updatesContent() {
        TaskNoteRequest request = new TaskNoteRequest("Updated content");
        TaskNote saved = TaskNote.builder().id(NOTE_ID).task(task).content("Updated content").build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(noteRepository.findByTaskId(TASK_ID)).thenReturn(Optional.of(note));
        when(noteRepository.save(any())).thenReturn(saved);

        TaskNoteResponse result = service.upsertNote(TASK_ID, request, USER_ID);

        ArgumentCaptor<TaskNote> captor = ArgumentCaptor.forClass(TaskNote.class);
        verify(noteRepository).save(captor.capture());
        assertEquals("Updated content", captor.getValue().getContent());
        assertEquals("Updated content", result.content());
    }

    @Test
    void upsertNote_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.upsertNote(TASK_ID, new TaskNoteRequest("content"), USER_ID));
    }
}
