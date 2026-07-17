package com.justdoit.task.feature.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Cliente HTTP para o notification-service (espelho do TaskServiceClient do
 * auth-service). Notificação é best-effort: qualquer falha é logada e engolida —
 * nunca derruba a operação de negócio que a disparou.
 *
 * Dois caminhos:
 * - {@link #notifyTaskCompleted}: fluxo com usuário presente — repassa o token da
 *   própria request (POST /notifications, userId sai do JWT lá).
 * - {@link #notifyTaskOverdue}: fluxo de job, sem usuário — usa o endpoint interno
 *   (POST /internal/notifications) autenticado por segredo compartilhado de env.
 */
@Slf4j
@Component
public class NotificationClient {

    private final RestClient restClient;
    private final String internalToken;

    public NotificationClient(@Value("${app.notification-service.url:http://localhost:8083}") String baseUrl,
                              @Value("${app.internal-token:}") String internalToken) {
        // Timeouts curtos: o envio roda no caminho da request do usuário (após o
        // commit) ou dentro do job — um notification-service lento não pode segurar.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        this.restClient = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
        this.internalToken = internalToken;
    }

    public void notifyTaskCompleted(String authorizationHeader, UUID taskId, String taskTitle) {
        try {
            restClient.post()
                    .uri("/notifications")
                    .header("Authorization", authorizationHeader)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "taskId", taskId,
                            "type", "TASK_COMPLETED",
                            "title", "Tarefa concluída",
                            "message", "Você concluiu a tarefa \"" + truncate(taskTitle) + "\"."))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Falha (ignorada) ao notificar conclusão da task {}: {}", taskId, e.getMessage());
        }
    }

    public void notifyTaskOverdue(UUID userId, UUID taskId, String taskTitle) {
        if (internalToken == null || internalToken.isBlank()) {
            // Sem INTERNAL_API_TOKEN configurado não há como autenticar no endpoint
            // interno — o job segue marcando OVERDUE, só não notifica.
            log.debug("INTERNAL_API_TOKEN ausente; notificação de atraso da task {} não enviada", taskId);
            return;
        }
        try {
            Map<String, Object> body = new HashMap<>();
            body.put("userId", userId);
            body.put("taskId", taskId);
            body.put("type", "TASK_OVERDUE");
            body.put("title", "Tarefa atrasada");
            body.put("message", "A tarefa \"" + truncate(taskTitle) + "\" passou do prazo.");
            restClient.post()
                    .uri("/internal/notifications")
                    .header("X-Internal-Token", internalToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Falha (ignorada) ao notificar atraso da task {}: {}", taskId, e.getMessage());
        }
    }

    // O título da tarefa pode ter até 200 chars e a notificação aceita message de
    // até 2000 — truncar aqui é só cinto de segurança para não estourar o limite.
    private static String truncate(String title) {
        if (title == null) return "";
        return title.length() <= 150 ? title : title.substring(0, 147) + "...";
    }
}
