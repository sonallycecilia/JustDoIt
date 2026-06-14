package com.justdoit.auth.feature.auth;

import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RegisterRequest;
import com.justdoit.auth.config.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // @Mock cria um dublê de cada dependência
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenRepository jwtTokenRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtUtil jwtUtil;

    // @InjectMocks cria o AuthService real e injeta os mocks acima
    @InjectMocks private AuthService authService;

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
        when(jwtUtil.generateToken(any(), eq("maria@email.com"), eq("USER"))).thenReturn("token.gerado.aqui");
        when(jwtTokenRepository.save(any(JwtToken.class))).thenReturn(null);

        // Act — executa o método que queremos testar
        AuthResponse response = authService.register(request);

        // Assert — verifica o resultado
        assertThat(response).isNotNull();
        assertThat(response.token()).isEqualTo("token.gerado.aqui");
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
                .hasMessage("Email already registered");

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
        when(jwtUtil.generateToken(any(), any(), any())).thenReturn("token");
        when(jwtTokenRepository.save(any())).thenReturn(null);

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
        when(jwtUtil.generateToken(any(), eq("maria@email.com"), eq("USER"))).thenReturn("token.valido");
        when(jwtTokenRepository.save(any())).thenReturn(null);

        AuthResponse response = authService.login(request);

        assertThat(response.token()).isEqualTo("token.valido");
    }

    @Test
    @DisplayName("login: deve lançar exceção quando email não existe")
    void login_deveLancarExcecao_quandoEmailNaoExiste() {
        LoginRequest request = new LoginRequest("naoexiste@email.com", "senha123");

        when(userRepository.findByEmail("naoexiste@email.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid credentials");
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
                .hasMessage("Invalid credentials");

        // Garante que o token NÃO foi gerado
        verify(jwtUtil, never()).generateToken(any(), any(), any());
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
        when(passwordEncoder.matches("errada", "hash")).thenReturn(false);

        assertThatThrownBy(() -> authService.login(emailErrado))
                .hasMessage("Invalid credentials");

        assertThatThrownBy(() -> authService.login(senhaErrada))
                .hasMessage("Invalid credentials");
    }
}
