package com.justdoit.auth.shared;

import com.justdoit.common.validation.TextoSeguro;
import com.justdoit.common.validation.SenhaForte; 
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
    @NotBlank @Size(max = 120) @TextoSeguro String name,
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @SenhaForte @Size(max = 100) String password, 
    @NotNull LocalDate birthDate
) {}