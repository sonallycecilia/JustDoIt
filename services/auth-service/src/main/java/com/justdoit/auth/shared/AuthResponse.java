package com.justdoit.auth.shared;

public record AuthResponse(String accessToken, String refreshToken, long expiresIn) {}
