package com.justdoit.auth.feature.auth;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import com.justdoit.auth.shared.TurnstileResponse;

@Slf4j
@Service
public class TurnstileService {

    private final RestClient restClient;
    private final String verifyUrl;
    private final String secretKey;

    public TurnstileService(
            RestClient.Builder restClientBuilder,
            @Value("${cloudflare.turnstile.verify-url}") String verifyUrl,
            @Value("${cloudflare.turnstile.secret-key}") String secretKey) {
        // Inicializa o cliente HTTP moderno do Spring
        this.restClient = restClientBuilder.build();
        this.verifyUrl = verifyUrl;
        this.secretKey = secretKey;
    }

    /**
     * Comunica com a Cloudflare para verificar se o token é válido.
     */
    public boolean isValid(String token) {
        if (token == null || token.isBlank()) {
            log.warn("Turnstile: Token ausente na requisição.");
            return false;
        }

        try {
            // A Cloudflare espera os dados no formato x-www-form-urlencoded
            MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
            formData.add("secret", secretKey);
            formData.add("response", token);

            // Dispara a requisição HTTP POST
            TurnstileResponse response = restClient.post()
                    .uri(verifyUrl)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formData)
                    .retrieve()
                    .body(TurnstileResponse.class);

            if (response != null && response.success()) {
                return true;
            } else {
                log.warn("Turnstile: Validação falhou. Erros da Cloudflare: {}", 
                         response != null ? response.errorCodes() : "Resposta nula");
                return false;
            }
        } catch (Exception e) {
            // Se a própria Cloudflare cair (timeout, indisponibilidade), 
            // a decisão de bloquear ou permitir depende do quão crítico é o sistema.
            // Para não travar usuários legítimos por culpa de terceiros,
            // poderíamos retornar 'true', mas o mais seguro inicialmente é lançar um erro 502/503.
            log.error("Turnstile: Falha de comunicação com a API da Cloudflare.", e);
            throw new RuntimeException("Serviço de verificação indisponível no momento. Tente novamente.");
        }
    }
}