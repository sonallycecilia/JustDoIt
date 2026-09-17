package com.justdoit.auth.feature.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para o task-service. Usado na exclusão de conta para remover os
 * dados do usuário (tarefas e categorias) que vivem naquele serviço.
 */
@Component
public class TaskServiceClient {

    private final RestClient restClient;

    public TaskServiceClient(@Value("${app.task-service.url:http://localhost:8081}") String baseUrl) {
        this.restClient = RestClient.builder().baseUrl(baseUrl).build();
    }

    /**
     * Remove no task-service todos os dados do usuário. Repassa o mesmo token do
     * usuário (Authorization), validado lá com o mesmo segredo JWT.
     */
    public void deleteUserData(String authorizationHeader) {
        restClient.delete()
                .uri("/me/data")
                .header("Authorization", authorizationHeader)
                .retrieve()
                .toBodilessEntity();
    }
}
