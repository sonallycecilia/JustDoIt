package com.justdoit.task.feature.task;

import com.justdoit.task.feature.category.Category;
import com.justdoit.task.feature.category.CategoryRepository;
import com.justdoit.task.shared.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock private TaskRepository taskRepository;
    @Mock private SubTaskRepository subTaskRepository;
    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private TaskService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID TASK_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID CAT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000003");

    private Task task;
    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder().id(CAT_ID).userId(USER_ID).name("Work").color("#FF0000").build();
        task = Task.builder().id(TASK_ID).userId(USER_ID).title("Test task")
                .status(TaskStatus.PENDING).priority(Priority.NORMAL).build();
    }

    @Test
    void createTask_withoutCategory_savesTask() {
        TaskRequest request = new TaskRequest("Test task", null, null, null);
        when(taskRepository.save(any())).thenReturn(task);

        TaskResponse result = service.createTask(request, USER_ID);

        assertEquals(TASK_ID, result.id());
        assertEquals("Test task", result.title());
        assertEquals(TaskStatus.PENDING, result.status());
        verify(taskRepository).save(any(Task.class));
    }

    @Test
    void createTask_withCategory_loadsCategory() {
        TaskRequest request = new TaskRequest("Test task", null, CAT_ID, Priority.URGENT_IMPORTANT);
        Task taskWithCat = Task.builder().id(TASK_ID).userId(USER_ID).title("Test task")
                .category(category).status(TaskStatus.PENDING).priority(Priority.URGENT_IMPORTANT).build();
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.of(category));
        when(taskRepository.save(any())).thenReturn(taskWithCat);

        TaskResponse result = service.createTask(request, USER_ID);

        assertEquals(CAT_ID, result.categoryId());
        verify(categoryRepository).findByIdAndUserId(CAT_ID, USER_ID);
    }

    @Test
    void createTask_categoryNotFound_throwsException() {
        TaskRequest request = new TaskRequest("Test task", null, CAT_ID, null);
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.createTask(request, USER_ID));
    }

    @Test
    void getTaskById_returnsResponse() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));

        TaskResponse result = service.getTaskById(TASK_ID, USER_ID);

        assertEquals(TASK_ID, result.id());
        assertEquals("Test task", result.title());
    }

    @Test
    void getTaskById_notFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getTaskById(TASK_ID, USER_ID));
    }

    @Test
    void getAllTasksByUser_returnsList() {
        when(taskRepository.findByUserId(USER_ID)).thenReturn(List.of(task));

        List<TaskResponse> result = service.getAllTasksByUser(USER_ID);

        assertEquals(1, result.size());
        assertEquals(TASK_ID, result.get(0).id());
    }

    @Test
    void updateTask_updatesFieldsAndSaves() {
        TaskRequest request = new TaskRequest("Updated title", "desc", null, Priority.URGENT_IMPORTANT);
        Task updated = Task.builder().id(TASK_ID).userId(USER_ID).title("Updated title")
                .description("desc").status(TaskStatus.PENDING).priority(Priority.URGENT_IMPORTANT).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(updated);

        TaskResponse result = service.updateTask(TASK_ID, request, USER_ID);

        assertEquals("Updated title", result.title());
        assertEquals(Priority.URGENT_IMPORTANT, result.priority());
    }

    @Test
    void updateTask_notFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.updateTask(TASK_ID, new TaskRequest("t", null, null, null), USER_ID));
    }

    @Test
    void deleteTask_callsDelete() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));

        service.deleteTask(TASK_ID, USER_ID);

        verify(taskRepository).delete(task);
    }

    @Test
    void deleteTask_notFound_throwsException() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.deleteTask(TASK_ID, USER_ID));
    }

    @Test
    void completeTask_setsStatusCompleted() {
        Task completed = Task.builder().id(TASK_ID).userId(USER_ID).title("Test task")
                .status(TaskStatus.COMPLETED).priority(Priority.NORMAL).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(completed);

        TaskResponse result = service.completeTask(TASK_ID, USER_ID);

        assertEquals(TaskStatus.COMPLETED, result.status());
    }

    @Test
    void addSubTask_savesAndReturnsResponse() {
        SubTaskRequest request = new SubTaskRequest("Sub item", 1);
        SubTask subTask = SubTask.builder()
                .id(UUID.randomUUID()).task(task).title("Sub item")
                .status(TaskStatus.PENDING).position(1).build();
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(subTaskRepository.save(any())).thenReturn(subTask);

        SubTaskResponse result = service.addSubTask(TASK_ID, request, USER_ID);

        assertEquals("Sub item", result.title());
        assertEquals(TaskStatus.PENDING, result.status());
    }

    @Test
    void getSubTaskProgress_noSubTasks_returnsZero() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(subTaskRepository.countByTaskId(TASK_ID)).thenReturn(0L);

        double result = service.getSubTaskProgress(TASK_ID, USER_ID);

        assertEquals(0.0, result);
    }

    @Test
    void getSubTaskProgress_someCompleted_returnsRatio() {
        when(taskRepository.findByIdAndUserId(TASK_ID, USER_ID)).thenReturn(Optional.of(task));
        when(subTaskRepository.countByTaskId(TASK_ID)).thenReturn(4L);
        when(subTaskRepository.countByTaskIdAndStatus(TASK_ID, TaskStatus.COMPLETED)).thenReturn(2L);

        double result = service.getSubTaskProgress(TASK_ID, USER_ID);

        assertEquals(0.5, result);
    }
}
