package com.justdoit.auth.shared;

import com.justdoit.common.validation.TextoSeguro;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

// password não recebe @TextoSeguro de propósito: senha legitimamente contém
// < ' ; e barrá-los enfraqueceria a política de senhas.
public record RegisterRequest(
    @NotBlank @Size(max = 120) @TextoSeguro String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") String password,
    @NotNull LocalDate birthDate
) {}
