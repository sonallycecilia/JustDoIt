package com.justdoit.auth.feature.auth;

import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RegisterRequest;
import com.justdoit.auth.config.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @Mock cria um dublê de cada dependência
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    // @InjectMocks cria o AuthService real e injeta os mocks acima
    @InjectMocks private AuthService authService;

    @BeforeEach
    void configurarExpiracaoDoRefreshToken() {
        // @Value não é resolvido em teste unitário; setamos manualmente (7 dias).
        ReflectionTestUtils.setField(authService, "refreshTokenExpirationMs", 604_800_000L);
    }

    // ─────────────────────────────────────────────
    // register()
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("register: deve retornar token quando dados são válidos")
    void register_deveRetornarToken_quandoDadosValidos() {
        // Arrange — prepara os dados e diz o que os mocks devem retornar
        RegisterRequest request = new RegisterRequest(
                "Maria Silva", "maria@email.com", "senha123", LocalDate.of(2000, 1, 1)
        );

        User usuarioSalvo = User.builder()
                .id(UUID.randomUUID())
                .name("Maria Silva")
                .email("maria@email.com")
                .passwordHash("hash_da_senha")
                .build();

        when(userRepository.existsByEmail("maria@email.com")).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("hash_da_senha");
        when(userRepository.save(any(User.class))).thenReturn(usuarioSalvo);
        when(jwtUtil.generateAccessToken(any(), eq("maria@email.com"), eq("USER"))).thenReturn("token.gerado.aqui");
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenReturn(null);

        // Act — executa o método que queremos testar
        AuthResponse response = authService.register(request);

        // Assert — verifica o resultado
        assertThat(response).isNotNull();
        assertThat(response.accessToken()).isEqualTo("token.gerado.aqui");
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("register: deve lançar exceção quando email já está cadastrado")
    void register_deveLancarExcecao_quandoEmailJaExiste() {
        RegisterRequest request = new RegisterRequest(
                "João", "joao@email.com", "senha123", LocalDate.of(1995, 5, 10)
        );

        when(userRepository.existsByEmail("joao@email.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Email já cadastrado");

        // Garante que o banco NÃO foi chamado para salvar
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("register: deve salvar a senha como hash, nunca em texto puro")
    void register_deveSalvarSenhaComoHash() {
        RegisterRequest request = new RegisterRequest(
                "Ana", "ana@email.com", "senha123", LocalDate.of(1998, 3, 15)
        );

        User usuarioSalvo = User.builder().id(UUID.randomUUID()).email("ana@email.com").build();

        when(userRepository.existsByEmail(any())).thenReturn(false);
        when(passwordEncoder.encode("senha123")).thenReturn("$2a$10$hashBcrypt");
        when(userRepository.save(any())).thenReturn(usuarioSalvo);
        when(jwtUtil.generateAccessToken(any(), any(), any())).thenReturn("token");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        authService.register(request);

        // Captura o objeto User que foi passado para o save()
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        assertThat(captor.getValue().getPasswordHash()).isEqualTo("$2a$10$hashBcrypt");
        assertThat(captor.getValue().getPasswordHash()).doesNotContain("senha123");
    }

    // ─────────────────────────────────────────────
    // login()
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("login: deve retornar token quando credenciais são válidas")
    void login_deveRetornarToken_quandoCredenciaisValidas() {
        LoginRequest request = new LoginRequest("maria@email.com", "senha123");

        User usuario = User.builder()
                .id(UUID.randomUUID())
                .email("maria@email.com")
                .passwordHash("hash_da_senha")
                .build();

        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha123", "hash_da_senha")).thenReturn(true);
        when(jwtUtil.generateAccessToken(any(), eq("maria@email.com"), eq("USER"))).thenReturn("token.valido");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.login(request);

        assertThat(response.accessToken()).isEqualTo("token.valido");
        assertThat(response.refreshToken()).isNotBlank();
    }

    @Test
    @DisplayName("login: deve lançar exceção quando email não existe")
    void login_deveLancarExcecao_quandoEmailNaoExiste() {
        LoginRequest request = new LoginRequest("naoexiste@email.com", "senha123");

        when(userRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credenciais inválidas");
    }

    @Test
    @DisplayName("login: deve executar bcrypt mesmo quando email não existe (sem oráculo de timing)")
    void login_deveExecutarBcrypt_mesmoQuandoEmailNaoExiste() {
        // Sem o hash dummy, e-mail inexistente falharia rápido (sem bcrypt) e
        // permitiria enumerar contas medindo o tempo de resposta.
        when(passwordEncoder.encode(anyString())).thenReturn("$2a$10$hashDummy");
        authService.initDummyPasswordHash();

        LoginRequest request = new LoginRequest("naoexiste@email.com", "senha123");
        when(userRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .hasMessage("Credenciais inválidas");

        verify(passwordEncoder).matches("senha123", "$2a$10$hashDummy");
    }

    @Test
    @DisplayName("login: deve lançar exceção quando senha está errada")
    void login_deveLancarExcecao_quandoSenhaErrada() {
        LoginRequest request = new LoginRequest("maria@email.com", "senha_errada");

        User usuario = User.builder()
                .id(UUID.randomUUID())
                .email("maria@email.com")
                .passwordHash("hash_da_senha_correta")
                .build();

        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches("senha_errada", "hash_da_senha_correta")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credenciais inválidas");

        // Garante que o token NÃO foi gerado
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("login: não deve vazar qual dado está errado (email ou senha)")
    void login_deveDarMesmoErroPraEmailESenhaErrados() {
        // Boa prática de segurança: mesma mensagem para email inválido e senha inválida
        LoginRequest emailErrado = new LoginRequest("x@x.com", "qualquer");
        LoginRequest senhaErrada = new LoginRequest("maria@email.com", "errada");

        User usuario = User.builder().id(UUID.randomUUID()).email("maria@email.com")
                .passwordHash("hash").build();

        when(userRepository.findByEmail("x@x.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmail("maria@email.com")).thenReturn(Optional.of(usuario));
        // matcher amplo: o caminho de e-mail inexistente também chama matches()
        // (contra o hash dummy, mitigação de timing) e deve falhar igualmente
        when(passwordEncoder.matches(anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(emailErrado))
                .hasMessage("Credenciais inválidas");

        assertThatThrownBy(() -> authService.login(senhaErrada))
                .hasMessage("Credenciais inválidas");
    }

    // ─────────────────────────────────────────────
    // refresh()
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("refresh: deve emitir novo par de tokens e rotacionar o refresh token")
    void refresh_deveEmitirNovosTokens_eRotacionar() {
        UUID userId = UUID.randomUUID();
        RefreshToken stored = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash-armazenado")
                .userId(userId)
                .email("maria@email.com")
                .profile("USER")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .build();
        User usuario = User.builder().id(userId).email("maria@email.com").build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(stored));
        when(userRepository.findById(userId)).thenReturn(Optional.of(usuario));
        when(jwtUtil.generateAccessToken(any(), eq("maria@email.com"), eq("USER"))).thenReturn("novo.access");
        when(refreshTokenRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.refresh("refresh-token-em-claro");

        assertThat(response.accessToken()).isEqualTo("novo.access");
        assertThat(response.refreshToken()).isNotBlank();
        // rotação: o token usado vira "lápide" (usedAt preenchido) para que um
        // reuso futuro seja detectável — não é mais apagado na hora
        assertThat(stored.getUsedAt()).isNotNull();
        verify(refreshTokenRepository).save(stored);
    }

    @Test
    @DisplayName("refresh: reuso de token já rotacionado deve revogar todas as sessões do usuário")
    void refresh_deveRevogarTodasAsSessoes_quandoTokenJaRotacionadoForReusado() {
        UUID userId = UUID.randomUUID();
        RefreshToken jaUsado = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash-ja-usado")
                .userId(userId)
                .email("maria@email.com")
                .profile("USER")
                .expiresAt(LocalDateTime.now().plusDays(1))
                .usedAt(LocalDateTime.now().minusMinutes(5)) // já foi rotacionado antes
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(jaUsado));

        assertThatThrownBy(() -> authService.refresh("token-roubado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token inválido");

        // detecção de reuso: TODAS as sessões do usuário são derrubadas
        verify(refreshTokenRepository).deleteByUserId(userId);
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("refresh: deve lançar exceção quando o refresh token não existe")
    void refresh_deveLancarExcecao_quandoTokenInexistente() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.refresh("token-invalido"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token inválido");

        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    @Test
    @DisplayName("refresh: deve invalidar e rejeitar quando o refresh token está expirado")
    void refresh_deveRejeitar_quandoTokenExpirado() {
        RefreshToken expirado = RefreshToken.builder()
                .id(UUID.randomUUID())
                .tokenHash("hash-expirado")
                .userId(UUID.randomUUID())
                .email("maria@email.com")
                .profile("USER")
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expirado));

        assertThatThrownBy(() -> authService.refresh("token-expirado"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Refresh token inválido");

        verify(refreshTokenRepository).delete(expirado);
        verify(jwtUtil, never()).generateAccessToken(any(), any(), any());
    }

    // ─────────────────────────────────────────────
    // logout()
    // ─────────────────────────────────────────────

    @Test
    @DisplayName("logout: deve revogar (apagar) os refresh tokens do usuário")
    void logout_deveRevogarRefreshTokens() {
        UUID userId = UUID.randomUUID();

        authService.logout(userId);

        verify(refreshTokenRepository).deleteByUserId(userId);
    }
}
