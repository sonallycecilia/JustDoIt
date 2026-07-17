package com.justdoit.task.feature.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.task.config.JwtUtil;
import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TaskController.class)
class TaskControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private TaskService taskService;
    @MockitoBean private JwtUtil jwtUtil;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private TaskResponse taskResponse;

    @BeforeEach
    void setUp() {
        when(jwtUtil.extractUserId(anyString())).thenReturn(USER_ID);
        taskResponse = new TaskResponse(TASK_ID, USER_ID, null, "Test task", null,
                TaskStatus.PENDING, Priority.NORMAL, null, null, LocalDateTime.now(), LocalDateTime.now(), null);
    }

    @Test
    @WithMockUser
    void createTask_returnsCreated() throws Exception {
        TaskRequest request = new TaskRequest("Test task", null, null, null, null, null);
        when(taskService.createTask(any(), eq(USER_ID))).thenReturn(taskResponse);

        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(TASK_ID.toString()))
                .andExpect(jsonPath("$.title").value("Test task"));
    }

    @Test
    @WithMockUser
    void createTask_withBlankTitle_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/tasks")
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("", null, null, null, null, null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser
    void getAllTasks_returnsOk() throws Exception {
        when(taskService.getAllTasksByUser(USER_ID)).thenReturn(List.of(taskResponse));

        mockMvc.perform(get("/tasks")
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(TASK_ID.toString()));
    }

    @Test
    @WithMockUser
    void getTaskById_returnsOk() throws Exception {
        when(taskService.getTaskById(TASK_ID, USER_ID)).thenReturn(taskResponse);

        mockMvc.perform(get("/tasks/{id}", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(TASK_ID.toString()));
    }

    @Test
    @WithMockUser
    void getTaskById_notFound_returns404() throws Exception {
        when(taskService.getTaskById(TASK_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/tasks/{id}", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void updateTask_returnsOk() throws Exception {
        TaskRequest request = new TaskRequest("Updated", null, null, null, null, null);
        TaskResponse updated = new TaskResponse(TASK_ID, USER_ID, null, "Updated", null,
                TaskStatus.PENDING, Priority.NORMAL, null, null, LocalDateTime.now(), LocalDateTime.now(), null);
        when(taskService.updateTask(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(updated);

        mockMvc.perform(put("/tasks/{id}", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Updated"));
    }

    @Test
    @WithMockUser
    void updateTask_notFound_returns404() throws Exception {
        when(taskService.updateTask(eq(TASK_ID), any(), eq(USER_ID)))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(put("/tasks/{id}", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new TaskRequest("t", null, null, null, null, null))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void deleteTask_returnsNoContent() throws Exception {
        doNothing().when(taskService).deleteTask(TASK_ID, USER_ID);

        mockMvc.perform(delete("/tasks/{id}", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser
    void deleteTask_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(taskService).deleteTask(TASK_ID, USER_ID);

        mockMvc.perform(delete("/tasks/{id}", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser
    void completeTask_returnsOk() throws Exception {
        TaskResponse completed = new TaskResponse(TASK_ID, USER_ID, null, "Test task", null,
                TaskStatus.COMPLETED, Priority.NORMAL, null, null, LocalDateTime.now(), LocalDateTime.now(), null);
        when(taskService.completeTask(eq(TASK_ID), eq(USER_ID), anyString())).thenReturn(completed);

        mockMvc.perform(patch("/tasks/{id}/complete", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));
    }

    @Test
    @WithMockUser
    void addSubTask_returnsCreated() throws Exception {
        SubTaskRequest request = new SubTaskRequest("Sub item", 1);
        SubTaskResponse subResponse = new SubTaskResponse(UUID.randomUUID(), "Sub item", TaskStatus.PENDING, 1);
        when(taskService.addSubTask(eq(TASK_ID), any(), eq(USER_ID))).thenReturn(subResponse);

        mockMvc.perform(post("/tasks/{id}/subtasks", TASK_ID)
                        .with(csrf())
                        .header("Authorization", "Bearer mock-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Sub item"));
    }

    @Test
    @WithMockUser
    void getSubTaskProgress_returnsOk() throws Exception {
        when(taskService.getSubTaskProgress(TASK_ID, USER_ID)).thenReturn(0.75);

        mockMvc.perform(get("/tasks/{id}/subtasks/progress", TASK_ID)
                        .header("Authorization", "Bearer mock-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").value(0.75));
    }
}
