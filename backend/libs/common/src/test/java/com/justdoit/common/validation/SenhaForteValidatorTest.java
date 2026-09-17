package com.justdoit.common.validation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste unitário da regra de política de senha usada por {@link SenhaForte}.
 */
class SenhaForteValidatorTest {

    private final SenhaForteValidator validator = new SenhaForteValidator();

    private boolean aceita(String senha) {
        return validator.isValid(senha, null);
    }

    // ─────────────────────────────────────────────
    // Deve REJEITAR
    // ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "abc12345",       // sem maiúscula
            "ABC12345",       // sem minúscula
            "AbcDefgh",       // sem número
            "Ab1",            // curta demais, mesmo com os 3 tipos
            "12345678",       // só números
            "abcdefgh",       // só minúscula
            "ABCDEFGH",       // só maiúscula
    })
    @DisplayName("deve rejeitar senha que não atende à política")
    void deveRejeitarSenhaFraca(String senha) {
        assertThat(aceita(senha))
                .as("senha deveria ter sido rejeitada: %s", senha)
                .isFalse();
    }

    // ─────────────────────────────────────────────
    // Deve ACEITAR
    // ─────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
            "Senha123",
            "MinhaSenh4Forte",
            "Abcdefg1",       // exatamente 8 chars, no limite mínimo
            "SenhaComEspaco 1", // espaço no meio é permitido (senha pode conter qualquer caractere)
    })
    @DisplayName("deve aceitar senha que atende à política")
    void deveAceitarSenhaForte(String senha) {
        assertThat(aceita(senha))
                .as("senha deveria ter sido aceita: %s", senha)
                .isTrue();
    }

    // ─────────────────────────────────────────────
    // Casos de borda
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("nulo e vazio passam: presença é responsabilidade do @NotBlank ou da regra de negócio")
    void nuloEVazioPassam() {
        assertThat(aceita(null)).isTrue();
        assertThat(aceita("")).isTrue();
        assertThat(aceita("   ")).isTrue();
    }
}