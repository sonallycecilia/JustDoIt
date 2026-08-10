package com.justdoit.task.feature.weeklyclosure.domain;

import com.justdoit.task.feature.task.TaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Garante que todo usuário sempre tenha um ciclo semanal (WeeklyCycle) OPEN
 * disponível para trabalhar.
 *
 * Antes desta classe, o ÚNICO lugar que criava um WeeklyCycle era
 * WeeklyClosureService.createNextCycle(), chamado apenas DURANTE o fechamento
 * de um ciclo já existente. Ou seja, um usuário novo (ou qualquer usuário
 * cuja tabela weekly_cycles esteja vazia) nunca tinha um primeiro ciclo
 * criado — a prévia de fechamento (GET /weekly-cycles/current/closure-preview)
 * sempre lançava "Nenhum ciclo aberto encontrado para o usuário.".
 *
 * getOrCreateCurrentCycle() resolve isso: busca o ciclo OPEN mais recente do
 * usuário e, se não existir nenhum, cria um cobrindo a semana corrente
 * (segunda 00:00:00 até domingo 23:59:59).
 *
 * Além disso, "adota" para esse ciclo qualquer tarefa do usuário que tenha
 * ficado com cycle_id NULL (tarefas criadas antes da correção acima existir).
 * Sem isso, tarefas legadas nunca aparecem em nenhuma prévia/fechamento —
 * elas ficam visíveis no To Do só na tela, mas invisíveis pro Encerramento
 * Semanal, porque toda a consulta de fechamento filtra por cycle_id.
 */
@Service
public class WeeklyCycleProvisioningService {

    private final WeeklyCycleRepository cycleRepository;
    private final TaskRepository taskRepository;

    public WeeklyCycleProvisioningService(WeeklyCycleRepository cycleRepository, TaskRepository taskRepository) {
        this.cycleRepository = cycleRepository;
        this.taskRepository = taskRepository;
    }

    @Transactional
    public WeeklyCycle getOrCreateCurrentCycle(UUID userId) {
        WeeklyCycle currentCycle = cycleRepository.findFirstByUserIdAndStatusOrderByStartDateDesc(userId, CycleStatus.OPEN)
                .orElseGet(() -> createCycleForCurrentWeek(userId));

        // Idempotente: só existem linhas com cycle_id NULL na primeira vez
        // que um usuário passa por aqui depois do deploy desta correção;
        // nas chamadas seguintes o UPDATE não afeta nenhuma linha.
        taskRepository.adoptOrphanTasks(userId, currentCycle.getId());

        return currentCycle;
    }

    private WeeklyCycle createCycleForCurrentWeek(UUID userId) {
        LocalDateTime start = LocalDate.now().with(DayOfWeek.MONDAY).atStartOfDay();
        LocalDateTime end = start.plusDays(7).minusSeconds(1);

        WeeklyCycle cycle = new WeeklyCycle(
                null,
                userId,
                start,
                end,
                CycleStatus.OPEN,
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        return cycleRepository.save(cycle);
    }
}
