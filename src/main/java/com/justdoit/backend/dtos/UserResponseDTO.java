package com.justdoit.backend.dtos;

import com.justdoit.backend.models.User;

public record UserResponseDTO(
        Long id,
        String name,
        String email
) {
    public static UserResponseDTO from(User user) {
        return new UserResponseDTO(user.getId(), user.getName(), user.getEmail());
    }
}
