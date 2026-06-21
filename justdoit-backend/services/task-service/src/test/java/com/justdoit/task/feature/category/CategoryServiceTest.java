package com.justdoit.task.feature.category;

import com.justdoit.task.shared.CategoryRequest;
import com.justdoit.task.shared.CategoryResponse;
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
class CategoryServiceTest {

    @Mock private CategoryRepository categoryRepository;
    @InjectMocks private CategoryService service;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private Category category;

    @BeforeEach
    void setUp() {
        category = Category.builder()
                .id(CAT_ID).userId(USER_ID).name("Work").color("#FF0000").description("Work tasks")
                .build();
    }

    @Test
    void getAllByUser_returnsList() {
        when(categoryRepository.findByUserId(USER_ID)).thenReturn(List.of(category));

        List<CategoryResponse> result = service.getAllByUser(USER_ID);

        assertEquals(1, result.size());
        assertEquals(CAT_ID, result.get(0).id());
        assertEquals("Work", result.get(0).name());
    }

    @Test
    void getById_returnsResponse() {
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.of(category));

        CategoryResponse result = service.getById(CAT_ID, USER_ID);

        assertEquals(CAT_ID, result.id());
        assertEquals("Work", result.name());
        assertEquals("#FF0000", result.color());
    }

    @Test
    void getById_notFound_throwsException() {
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.getById(CAT_ID, USER_ID));
    }

    @Test
    void create_savesAndReturnsResponse() {
        CategoryRequest request = new CategoryRequest("Work", "#FF0000", "Work tasks");
        when(categoryRepository.save(any())).thenReturn(category);

        CategoryResponse result = service.create(request, USER_ID);

        assertEquals("Work", result.name());
        assertEquals("#FF0000", result.color());
        verify(categoryRepository).save(any(Category.class));
    }

    @Test
    void update_updatesFieldsAndSaves() {
        CategoryRequest request = new CategoryRequest("Personal", "#00FF00", "Personal tasks");
        Category updated = Category.builder()
                .id(CAT_ID).userId(USER_ID).name("Personal").color("#00FF00").description("Personal tasks")
                .build();
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.of(category));
        when(categoryRepository.save(any())).thenReturn(updated);

        CategoryResponse result = service.update(CAT_ID, request, USER_ID);

        assertEquals("Personal", result.name());
        assertEquals("#00FF00", result.color());
    }

    @Test
    void update_notFound_throwsException() {
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> service.update(CAT_ID, new CategoryRequest("n", "#FFF", null), USER_ID));
    }

    @Test
    void delete_callsDelete() {
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.of(category));

        service.delete(CAT_ID, USER_ID);

        verify(categoryRepository).delete(category);
    }

    @Test
    void delete_notFound_throwsException() {
        when(categoryRepository.findByIdAndUserId(CAT_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.delete(CAT_ID, USER_ID));
    }
}
