package com.justdoit.task.feature.task;

import com.justdoit.task.feature.timer.TaskTimer;

/**
 * Onde mora a duração estimada de uma tarefa.
 *
 * A estimativa que o usuário edita fica no módulo de cronômetro
 * (PUT /tasks/{id}/timer → TaskTimer.estimatedMinutes). Task.estimatedMinutes é a
 * coluna antiga, que nem createTask nem updateTask escrevem — sobra só como
 * fallback de dados legados.
 *
 * A regra vive aqui porque três caminhos precisam dela e precisam concordar:
 * TaskResponse (o que a lista de tarefas devolve), o relatório por período e a
 * exportação. Enquanto ela estava duplicada, TaskService.toResponse lia a coluna
 * legada e devolvia null para toda tarefa criada pelo app — o "planejado" do
 * dashboard nascia zerado.
 *
 * IMPORTANTE: quem chama precisa ter o timer carregado (join fetch) ou estar
 * dentro da transação; senão o acesso lazy vira N+1.
 */
public final class TaskEstimates {

    private TaskEstimates() { }

    /** Minutos estimados da tarefa, ou null quando ela não tem estimativa. */
    public static Integer minutesOf(Task task) {
        TaskTimer timer = task.getTimer();
        if (timer != null && timer.getEstimatedMinutes() != null) {
            return timer.getEstimatedMinutes();
        }
        return task.getEstimatedMinutes();
    }

    /** Idem, mas 0 no lugar de null — para somatórios. */
    public static int minutesOrZero(Task task) {
        Integer minutes = minutesOf(task);
        return minutes != null ? minutes : 0;
    }
}
