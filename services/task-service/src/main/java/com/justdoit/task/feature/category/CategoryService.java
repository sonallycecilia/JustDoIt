package com.justdoit.task.feature.category;

import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.shared.CategoryRequest;
import com.justdoit.task.shared.CategoryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final TaskRepository taskRepository;

    public List<CategoryResponse> getAllByUser(UUID userId) {
        return categoryRepository.findByUserId(userId).stream()
                .map(this::toResponse)
                .toList();
    }

    public CategoryResponse getById(UUID id, UUID userId) {
        return toResponse(categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found")));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request, UUID userId) {
        Category category = Category.builder()
                .userId(userId)
                .name(request.name())
                .color(request.color())
                .description(request.description())
                .build();
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse update(UUID id, CategoryRequest request, UUID userId) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        category.setName(request.name());
        category.setColor(request.color());
        category.setDescription(request.description());
        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Category category = categoryRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Category not found"));
        // Reatribui as tarefas desta categoria para "Genérico" (category_id = null)
        // antes de remover, evitando violação de FK e tarefas órfãs.
        List<Task> tasks = taskRepository.findByCategoryIdAndUserId(id, userId);
        tasks.forEach(task -> task.setCategory(null));
        taskRepository.saveAll(tasks);
        categoryRepository.delete(category);
    }

    private CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getUserId(), c.getName(), c.getColor(), c.getDescription());
    }
}
