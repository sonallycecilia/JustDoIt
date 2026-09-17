package com.justdoit.task.feature.timer;

/**
 * O usuário já tem um cronômetro ativo em alguma tarefa. Vira HTTP 409 no controller.
 *
 * <p>Distinta de {@code IllegalArgumentException} (que o controller mapeia para 404) porque
 * "conflito" e "não encontrado" são situações diferentes para o cliente: no 409 o pedido é
 * legítimo, só chegou depois de outro.
 */
public class CronometroJaAtivoException extends RuntimeException {

    public CronometroJaAtivoException() {
        super("Já existe um cronômetro ativo para este usuário");
    }
}
