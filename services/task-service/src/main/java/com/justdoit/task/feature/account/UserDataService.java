package com.justdoit.task.feature.account;

import com.justdoit.task.feature.category.Category;
import com.justdoit.task.feature.category.CategoryRepository;
import com.justdoit.task.feature.task.Task;
import com.justdoit.task.feature.task.TaskRepository;
import com.justdoit.task.feature.usernote.UserNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserDataService {

    private final TaskRepository taskRepository;
    private final CategoryRepository categoryRepository;
    private final UserNoteRepository userNoteRepository;

    /**
     * Remove todos os dados do usuário neste serviço: tarefas (com cascata para
     * subtarefas, notas, timers, sessões de foco, ciclo, etc.), categorias e o
     * bloco de anotações. Usado quando a conta é excluída no auth-service.
     */
    @Transactional
    public void deleteAllForUser(UUID userId) {
        // deleteAll(entidades) carrega cada tarefa e dispara o cascade JPA — diferente
        // de um delete em massa por query, que ignoraria as entidades relacionadas.
        List<Task> tasks = taskRepository.findByUserId(userId);
        if (!tasks.isEmpty()) taskRepository.deleteAll(tasks);

        List<Category> categories = categoryRepository.findByUserId(userId);
        if (!categories.isEmpty()) categoryRepository.deleteAll(categories);

        userNoteRepository.findByUserId(userId).ifPresent(userNoteRepository::delete);
    }
}
