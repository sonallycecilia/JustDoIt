package com.justdoit.task.feature.focussession;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.task.Task;

import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FocusSessionServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private FocusSessionRepository sessionRepository;
    @InjectMocks private FocusSessionService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID SESSION_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Task task;
    private FocusSession session;

    @BeforeEach
    void setUp() {
        task = Task.builder().id(TASK_ID).userId(USER_ID).title("Task").build();
        session = FocusSession.builder()
                .id(SESSION_ID).task(task)
                .focusMinutes(25).breakMinutes(5)
                .sessionType(SessionType.FOCUS)
                .startedAt(LocalDateTime.now())
                .completed(false)
                .build();
    }

    @Test
    void listSessions_returnsList() {
        FocusSession session2 = FocusSession.builder()
                .id(UUID.randomUUID()).task(task)
                .focusMinutes(50).sessionType(SessionType.BREAK).completed(true).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(sessionRepository.findByTaskId(TASK_ID)).thenReturn(List.of(session, session2));

        List<FocusSessionResponse> result = service.listSessions(TASK_ID, USER_ID);

        assertEquals(2, result.size());
        assertEquals(SESSION_ID, result.get(0).id());
        assertFalse(result.get(0).completed());
        assertTrue(result.get(1).completed());
    }

    @Test
    void listSessions_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.listSessions(TASK_ID, USER_ID));
    }

    @Test
    void createSession_createsAndReturns() {
        FocusSessionRequest request = new FocusSessionRequest(25, 5, SessionType.FOCUS,
                LocalDateTime.now(), null, null);
        FocusSession saved = FocusSession.builder()
                .id(SESSION_ID).task(task)
                .focusMinutes(25).breakMinutes(5)
                .sessionType(SessionType.FOCUS).completed(false).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(sessionRepository.save(any())).thenReturn(saved);

        FocusSessionResponse result = service.createSession(TASK_ID, request, USER_ID);

        assertEquals(SESSION_ID, result.id());
        assertEquals(TASK_ID, result.taskId());
        assertEquals(25, result.focusMinutes());
        assertFalse(result.completed());
        verify(sessionRepository).save(any(FocusSession.class));
    }

    @Test
    void createSession_whenCompletedNull_defaultsFalse() {
        FocusSessionRequest request = new FocusSessionRequest(25, 5, SessionType.FOCUS, null, null, null);
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(sessionRepository.save(any())).thenReturn(session);

        service.createSession(TASK_ID, request, USER_ID);

        verify(sessionRepository).save(argThat(s -> !s.getCompleted()));
    }

    @Test
    void completeSession_setsCompletedTrue() {
        FocusSession saved = FocusSession.builder()
                .id(SESSION_ID).task(task).completed(true).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(sessionRepository.findByIdAndTaskId(SESSION_ID, TASK_ID)).thenReturn(Optional.of(session));
        when(sessionRepository.save(any())).thenReturn(saved);

        FocusSessionResponse result = service.completeSession(TASK_ID, SESSION_ID, USER_ID);

        verify(sessionRepository).save(argThat(FocusSession::getCompleted));
        assertTrue(result.completed());
    }

    @Test
    void completeSession_whenSessionNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(sessionRepository.findByIdAndTaskId(SESSION_ID, TASK_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.completeSession(TASK_ID, SESSION_ID, USER_ID));
    }

    @Test
    void deleteSession_deletesSession() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(sessionRepository.findByIdAndTaskId(SESSION_ID, TASK_ID)).thenReturn(Optional.of(session));

        service.deleteSession(TASK_ID, SESSION_ID, USER_ID);

        verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_whenTaskNotFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () ->
                service.deleteSession(TASK_ID, SESSION_ID, USER_ID));
    }
}
