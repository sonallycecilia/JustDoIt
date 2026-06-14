package com.justdoit.auth.feature.auth;

import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RegisterRequest;
import com.justdoit.auth.config.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtTokenRepository jwtTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

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
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), "USER");
        persistToken(user, token);
        return new AuthResponse(token);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), "USER");
        persistToken(user, token);
        return new AuthResponse(token);
    }

    private void persistToken(User user, String token) {
        JwtToken jwtToken = JwtToken.builder()
                .token(token)
                .userId(user.getId())
                .email(user.getEmail())
                .profile("USER")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        jwtTokenRepository.save(jwtToken);
    }
}
