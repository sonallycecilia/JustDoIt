package com.justdoit.task.feature.task;

import com.justdoit.task.shared.FocusSessionRequest;
import com.justdoit.task.shared.FocusSessionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FocusSessionService {

    private final TaskRepository taskRepository;
    private final FocusSessionRepository sessionRepository;

    public List<FocusSessionResponse> listSessions(UUID taskId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        return sessionRepository.findByTaskId(taskId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public FocusSessionResponse createSession(UUID taskId, FocusSessionRequest request, UUID userId) {
        Task task = taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        FocusSession session = FocusSession.builder()
                .task(task)
                .focusMinutes(request.focusMinutes())
                .breakMinutes(request.breakMinutes())
                .sessionType(request.sessionType())
                .startedAt(request.startedAt())
                .endedAt(request.endedAt())
                .completed(request.completed() != null ? request.completed() : false)
                .build();
        return toResponse(sessionRepository.save(session));
    }

    @Transactional
    public FocusSessionResponse completeSession(UUID taskId, UUID sessionId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        FocusSession session = sessionRepository.findByIdAndTaskId(sessionId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        session.setCompleted(true);
        return toResponse(sessionRepository.save(session));
    }

    @Transactional
    public void deleteSession(UUID taskId, UUID sessionId, UUID userId) {
        taskRepository.findByIdAndUserId(taskId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Task not found"));
        FocusSession session = sessionRepository.findByIdAndTaskId(sessionId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found"));
        sessionRepository.delete(session);
    }

    private FocusSessionResponse toResponse(FocusSession session) {
        return new FocusSessionResponse(
                session.getId(),
                session.getTask().getId(),
                session.getFocusMinutes(),
                session.getBreakMinutes(),
                session.getSessionType(),
                session.getStartedAt(),
                session.getEndedAt(),
                session.getCompleted()
        );
    }
}
