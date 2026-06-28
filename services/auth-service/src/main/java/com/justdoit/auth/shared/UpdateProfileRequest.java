package com.justdoit.auth.shared;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

// Atualização parcial do perfil: todos os campos são opcionais.
// - name/email: aplicados quando presentes e não em branco.
// - newPassword: exige currentPassword correto para ser aplicada.
// - avatarUrl: Data URL da foto (string vazia remove a foto).
public record UpdateProfileRequest(
    @Size(max = 120) String name,
    @Email @Size(max = 255) String email,
    String currentPassword,
    @Size(min = 8, max = 100, message = "Password must be between 8 and 100 characters") String newPassword,
    String avatarUrl
) {}
