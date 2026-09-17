package com.justdoit.auth.qualidade;

import com.justdoit.auth.config.JwtUtil;
import com.justdoit.auth.config.RateLimitFilter;
import com.justdoit.auth.feature.auth.AuthService;
import com.justdoit.auth.feature.auth.EmailVerifier;
import com.justdoit.auth.feature.auth.RefreshToken;
import com.justdoit.auth.feature.auth.RefreshTokenRepository;
import com.justdoit.auth.feature.auth.TaskServiceClient;
import com.justdoit.auth.feature.auth.User;
import com.justdoit.auth.feature.auth.UserRepository;
import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.common.security.JwtValidator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suíte agregadora da Taxa de Proteção do Ciclo de Sessão (TPS).
 *
 * <p>Denominador do backend: cinco comportamentos de segurança obrigatórios.
 * Cada cenário só incrementa o numerador depois de todas as suas asserções.
 * O {@code @AfterAll} publica a fórmula no log e funciona como gate exato de
 * 100%, de modo que remover, ignorar ou quebrar um cenário reprova a métrica.</p>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Métrica de segurança: proteção do ciclo de sessão (backend)")
class SessionLifecycleMetricsTest {

    private static final int TOTAL_SCENARIOS = 5;
    private static final AtomicInteger PASSED_SCENARIOS = new AtomicInteger();
    private static final String JWT_SECRET =
            "session-metrics-secret-with-at-least-thirty-two-bytes-2026";

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;
    @Mock private EmailVerifier emailVerifier;
    @Mock private TaskServiceClient taskServiceClient;

    @InjectMocks private AuthService authService;

    @BeforeEach
    void configureTokenDurations() {
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 43_200_000L);
        ReflectionTestUtils.setField(authService, "refreshTokenRememberExpirationMs", 2_592_000_000L);
        ReflectionTestUtils.setField(authService, "refreshTokenGracePeriodMs", 30_000L);
    }

    @AfterAll
    static void publishAndAssertMetric() {
        int numerator = PASSED_SCENARIOS.get();
        double tps = numerator * 100.0 / TOTAL_SCENARIOS;
        System.out.printf(Locale.ROOT,
                "[MÉTRICA SEGURANÇA - CICLO DE SESSÃO BACKEND] "
                        + "A=%d cenários corretos / B=%d cenários testados -> TPS = %.2f%%%n",
                numerator, TOTAL_SCENARIOS, tps);
        assertThat(numerator)
                .as("TPS do backend deve atingir exatamente 100%%")
                .isEqualTo(TOTAL_SCENARIOS);
    }

    @Test
    @DisplayName("1/5: JWT de acesso expirado é rejeitado")
    void rejectsExpiredAccessJwt() {
        JwtValidator validator = new JwtValidator();
        ReflectionTestUtils.setField(validator, "secret", JWT_SECRET);
        long now = System.currentTimeMillis();
        String expiredJwt = Jwts.builder()
                .subject(UUID.randomUUID().toString())
                .issuer(JwtValidator.ISSUER)
                .audience().add(JwtValidator.AUDIENCE).and()
                .claim("type", "access")
                .issuedAt(new Date(now - 120_000))
                .expiration(new Date(now - 60_000))
                .signWith(Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8)))
                .compact();

        assertThat(validator.validateToken(expiredJwt)).isFalse();
        markScenarioPassed();
    }

    @Test
    @DisplayName("2/5: refresh válido rotaciona o token e emite um novo par")
    void rotatesRefreshToken() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = activeRefreshToken(userId);
        User user = User.builder().id(userId).email("metricas@justdoit.app").build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(jwtUtil.generateAccessToken(userId, user.getEmail(), "USER")).thenReturn("new.access");
        when(jwtUtil.getAccessTokenExpirationMs()).thenReturn(900_000L);

        AuthResponse response = authService.refresh("valid-refresh-token");

        assertThat(response.accessToken()).isEqualTo("new.access");
        assertThat(response.refreshToken()).isNotBlank();
        assertThat(stored.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
        verify(refreshTokenRepository, times(2)).save(any(RefreshToken.class));
        markScenarioPassed();
    }

    @Test
    @DisplayName("3/5: reutilização fora da tolerância revoga todas as sessões")
    void revokesSessionsWhenRotatedTokenIsReused() {
        UUID userId = UUID.randomUUID();
        RefreshToken reused = activeRefreshToken(userId);
        reused.setUsedAt(LocalDateTime.now().minusMinutes(2));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(reused));

        assertThatThrownBy(() -> authService.refresh("reused-refresh-token"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token inválido");
        verify(refreshTokenRepository).deleteByUserId(userId);
        verify(jwtUtil, never()).generateAccessToken(any(), anyString(), anyString());
        markScenarioPassed();
    }

    @Test
    @DisplayName("4/5: logout revoga os refresh tokens do usuário")
    void revokesRefreshTokensOnLogout() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenRepository).deleteByUserId(userId);
        markScenarioPassed();
    }

    @Test
    @DisplayName("5/5: rate limiting bloqueia excesso com HTTP 429")
    void blocksExcessiveAuthenticationRequests() throws Exception {
        int capacity = 3;
        RateLimitFilter filter = new RateLimitFilter(true, capacity, 0.0001);

        for (int request = 0; request < capacity; request++) {
            assertThat(execute(filter, "/auth/login", "198.51.100.10")).isEqualTo(200);
        }
        assertThat(execute(filter, "/auth/login", "198.51.100.10")).isEqualTo(429);
        markScenarioPassed();
    }

    private static RefreshToken activeRefreshToken(UUID userId) {
        return RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("stored-hash")
                .userId(userId)
                .email("metricas@justdoit.app")
                .profile("USER")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .build();
    }

    private static int execute(RateLimitFilter filter, String path, String ip) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", path);
        request.setRequestURI(path);
        request.setRemoteAddr(ip);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response.getStatus();
    }

    private static void markScenarioPassed() {
        PASSED_SCENARIOS.incrementAndGet();
    }
}
