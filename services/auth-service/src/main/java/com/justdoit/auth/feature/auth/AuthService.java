package com.justdoit.auth.feature.auth;

import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.auth.shared.CheckEmailResponse;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RegisterRequest;
import com.justdoit.auth.shared.UpdateProfileRequest;
import com.justdoit.auth.shared.UserResponse;
import com.justdoit.auth.config.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String DEFAULT_PROFILE = "USER";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailVerifier emailVerifier;
    private final TaskServiceClient taskServiceClient;

    @Value("${jwt.refresh-token-expiration-ms:604800000}") // 7 dias
    private long refreshTokenExpirationMs;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email already registered");
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .birthDate(request.birthDate())
                .active(true)
                .build();
        user = userRepository.save(user);
        return issueTokens(user);
    }

    /**
     * Verificação prévia do e-mail (antes do cadastro): se já está registrado e
     * se o domínio aceita correio. Não lança exceção — sempre responde 200 com o
     * diagnóstico, deixando o frontend decidir a UX.
     */
    public CheckEmailResponse checkEmail(String email) {
        String normalized = email == null ? "" : email.trim();
        boolean registered = !normalized.isEmpty() && userRepository.existsByEmail(normalized);
        boolean deliverable = emailVerifier.isDeliverable(normalized);
        boolean available = !registered && deliverable;
        return new CheckEmailResponse(normalized, registered, deliverable, available);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        // Revoga sessões anteriores: apenas um refresh token ativo por usuário.
        refreshTokenRepository.deleteByUserId(user.getId());
        return issueTokens(user);
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        String hash = sha256(refreshTokenValue);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));

        boolean expired = stored.getExpiresAt().isBefore(LocalDateTime.now());
        // Rotação: o refresh token usado é sempre invalidado (válido ou expirado).
        refreshTokenRepository.delete(stored);
        if (expired) {
            throw new IllegalArgumentException("Invalid refresh token");
        }

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        return issueTokens(user);
    }

    @Transactional
    public void logout(UUID userId) {
        refreshTokenRepository.deleteByUserId(userId);
    }

    /**
     * Exclui definitivamente a conta do usuário: primeiro remove os dados de
     * tarefas/categorias no task-service (repassando o token do usuário) e, em
     * seguida, apaga refresh tokens e o próprio usuário. Se a purga das tarefas
     * falhar, a transação é revertida e a conta NÃO é excluída.
     */
    @Transactional
    public void deleteAccount(UUID userId, String authorizationHeader) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        taskServiceClient.deleteUserData(authorizationHeader);
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }

        if (request.email() != null && !request.email().isBlank()
                && !request.email().trim().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email().trim())) {
                throw new IllegalArgumentException("Email already registered");
            }
            user.setEmail(request.email().trim());
        }

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("Current password is incorrect");
            }
            user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        }

        // avatarUrl: presente => aplica; string vazia => remove a foto.
        if (request.avatarUrl() != null) {
            user.setAvatarUrl(request.avatarUrl().isBlank() ? null : request.avatarUrl());
        }

        user = userRepository.save(user);
        return toResponse(user);
    }

    private static UserResponse toResponse(User user) {
        return new UserResponse(user.getId(), user.getName(), user.getEmail(),
                user.getAvatarUrl(), user.getBirthDate(), user.getCreatedAt());
    }

    private AuthResponse issueTokens(User user) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), DEFAULT_PROFILE);
        String refreshTokenValue = generateRefreshTokenValue();
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(sha256(refreshTokenValue))
                .userId(user.getId())
                .email(user.getEmail())
                .profile(DEFAULT_PROFILE)
                .expiresAt(LocalDateTime.now().plus(refreshTokenExpirationMs, ChronoUnit.MILLIS))
                .build();
        refreshTokenRepository.save(refreshToken);
        return new AuthResponse(accessToken, refreshTokenValue, jwtUtil.getAccessTokenExpirationMs() / 1000);
    }

    private static String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
