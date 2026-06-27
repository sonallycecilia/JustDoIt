package com.justdoit.auth.shared;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        String avatarUrl,
        LocalDate birthDate,
        LocalDateTime createdAt
) {}
