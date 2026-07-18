package com.justdoit.task.feature.task;

import java.util.UUID;

/**
 * Evento de domínio publicado quando uma tarefa é concluída. Carrega o header
 * Authorization da request original para que o listener repasse o token do
 * usuário ao notification-service (padrão de comunicação com usuário presente).
 */
public record TaskCompletedEvent(UUID taskId, String title, String authorizationHeader) { }
