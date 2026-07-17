package com.justdoit.task.feature.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import com.justdoit.task.shared.CategoryRequest;
import com.justdoit.task.shared.CategoryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(CategoryController.class)
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private CategoryService categoryService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID CAT_ID  = UUID.fromString("00000000-0000-0000-0000-000000000002");

    private CategoryResponse categoryResponse;

    @BeforeEach
    void setUp() {
        categoryResponse = new CategoryResponse(CAT_ID, USER_ID, "Work", "#FF0000", "Work tasks");
    }

    @Test
    void getAll_returnsOk() throws Exception {
        when(categoryService.getAllByUser(USER_ID)).thenReturn(List.of(categoryResponse));

        mockMvc.perform(get("/categories")
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(CAT_ID.toString()))
                .andExpect(jsonPath("$[0].name").value("Work"));
    }

    @Test
    void getById_returnsOk() throws Exception {
        when(categoryService.getById(CAT_ID, USER_ID)).thenReturn(categoryResponse);

        mockMvc.perform(get("/categories/{id}", CAT_ID)
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Work"));
    }

    @Test
    void getById_notFound_returns404() throws Exception {
        when(categoryService.getById(CAT_ID, USER_ID)).thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(get("/categories/{id}", CAT_ID)
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }

    @Test
    void create_returnsCreated() throws Exception {
        CategoryRequest request = new CategoryRequest("Work", "#FF0000", "Work tasks");
        when(categoryService.create(any(), eq(USER_ID))).thenReturn(categoryResponse);

        mockMvc.perform(post("/categories")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(CAT_ID.toString()))
                .andExpect(jsonPath("$.name").value("Work"));
    }

    @Test
    void create_withBlankName_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/categories")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("", "#FFF", null))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_returnsOk() throws Exception {
        CategoryRequest request = new CategoryRequest("Personal", "#00FF00", null);
        CategoryResponse updated = new CategoryResponse(CAT_ID, USER_ID, "Personal", "#00FF00", null);
        when(categoryService.update(eq(CAT_ID), any(), eq(USER_ID))).thenReturn(updated);

        mockMvc.perform(put("/categories/{id}", CAT_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Personal"));
    }

    @Test
    void update_notFound_returns404() throws Exception {
        when(categoryService.update(eq(CAT_ID), any(), eq(USER_ID)))
                .thenThrow(new IllegalArgumentException("not found"));

        mockMvc.perform(put("/categories/{id}", CAT_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CategoryRequest("n", "#FFF", null))))
                .andExpect(status().isNotFound());
    }

    @Test
    void delete_returnsNoContent() throws Exception {
        doNothing().when(categoryService).delete(CAT_ID, USER_ID);

        mockMvc.perform(delete("/categories/{id}", CAT_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_notFound_returns404() throws Exception {
        doThrow(new IllegalArgumentException("not found")).when(categoryService).delete(CAT_ID, USER_ID);

        mockMvc.perform(delete("/categories/{id}", CAT_ID)
                        .with(csrf())
                        .with(authenticatedUser(USER_ID)))
                .andExpect(status().isNotFound());
    }
}
