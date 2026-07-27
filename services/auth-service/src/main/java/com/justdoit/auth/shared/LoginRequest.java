package com.justdoit.auth.shared;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String password,
    // "Manter conectado" na tela de login: define o prazo do refresh token.
    // Ausente no JSON (cliente antigo) = false, o padrão do primitivo.
    boolean rememberMe
) {
    public LoginRequest(String email, String password) {
        this(email, password, false);
    }
}
