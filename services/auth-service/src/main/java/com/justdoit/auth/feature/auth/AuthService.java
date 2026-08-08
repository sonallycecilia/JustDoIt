package com.justdoit.auth.feature.auth;

import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.auth.shared.CheckEmailResponse;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RegisterRequest;
import com.justdoit.auth.shared.UpdateProfileRequest;
import com.justdoit.auth.shared.UserResponse;
import com.justdoit.auth.config.JwtUtil;
import jakarta.annotation.PostConstruct;
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

    @Value("${jwt.refresh-token-expiration-ms:43200000}") // refresh token para sessões padrão (12 h) — sem "manter conectado"
    private long refreshTokenExpirationMs;

    @Value("${jwt.refresh-token-remember-expiration-ms:2592000000}") // refresh token com "manter conectado" (30 dias) — com "manter conectado"
    private long refreshTokenRememberExpirationMs;

    @Value("${jwt.refresh-token-grace-period-ms:30000}") // 30 s
    private long refreshTokenGracePeriodMs;

    // Hash bcrypt "sacrificial": usado no login quando o e-mail não existe, para
    // que a resposta demore o mesmo tempo com ou sem conta (sem oráculo de timing).
    private String dummyPasswordHash;

    @PostConstruct // gera um hash de senha falso
    void initDummyPasswordHash() {
        dummyPasswordHash = passwordEncoder.encode("dummy-" + UUID.randomUUID());
    }

    // recebe os dados  necessários, checa se o e-mail já existe, cria o usuário, salva no banco e emite tokens: acess e refresh.
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new IllegalArgumentException("Email já cadastrado");
        }
        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password())) // senha criptografada com bcrypt antes de ser salva no banco
                .birthDate(request.birthDate())
                .active(true)
                .build();
        user = userRepository.save(user);
        // Conta nova nasce como sessão curta, equivalente ao checkbox desmarcado.
        return issueTokens(user, false);
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
    // faz a busca pelo usuário pelo email
    //  se não existir, paga o custo do bcrypt mesmo assim
    // se existir, compara a senha com o hash armazenado, 
    // se não bater, lança exceção, se bater, revoga sessões anteriores e emite novos tokens.
    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).orElse(null);
        if (user == null) {
            // Paga o custo do bcrypt mesmo sem conta — e-mail inexistente e senha
            // errada respondem no mesmo tempo.
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new IllegalArgumentException("Credenciais inválidas");
        }
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }
        // Revoga sessões anteriores: apenas um refresh token ativo por usuário.
        refreshTokenRepository.deleteByUserId(user.getId()); // de forma prática: não é possivel estar logado em dois dispositivos ao mesmo tempo, pois ao logar em um, o outro é deslogado.
        return issueTokens(user, request.rememberMe());
    }

    @Transactional
    public AuthResponse refresh(String refreshTokenValue) {
        // recebe o token em texto puro e transformar em hash para buscar no banco
        // se não achar, barra a requisição lançando um erro
        String hash = sha256(refreshTokenValue);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido"));

        // aqui é o cálculo do prazo de expiração do refresh token
        //  se já passou do prazo, deleta o token e lança exceção
        if (stored.getExpiresAt().isBefore(LocalDateTime.now())) {
            refreshTokenRepository.delete(stored);
            throw new IllegalArgumentException("Refresh token inválido");
        }

        // LÓGICA DE ROTAÇÃO DO REFRESH TOKEN 

        // caso 1: token nunca usado antes 
        // nesse caso, marca o token como usado e salva no banco, setando a data e hora atual
        // ele não deleta, apenas marca como usado!!
        if (stored.getUsedAt() == null) {
            stored.setUsedAt(LocalDateTime.now());
            refreshTokenRepository.save(stored);
        
        // caso 2: token já usado antes - alerta de roubo de token
        // caso em que o token já foi usado e o tempo de tolerância de 30s já estourou 
        // oq indica que o token foi reutilizado e possivelmente por algum ataque
        } else if (stored.getUsedAt().plus(refreshTokenGracePeriodMs, ChronoUnit.MILLIS)
                .isBefore(LocalDateTime.now())) {
            // nesse caso, deleta o token do banco e lança exceção, forçando o usuário a logar novamente
            refreshTokenRepository.deleteByUserId(stored.getUserId());
            throw new IllegalArgumentException("Refresh token inválido");
        }
        

        // caso 3: o sistema entende que nenhum dos casos anteriores se aplicam
        //  ou seja, o token foi usado antes, mas ainda está dentro do prazo de tolerância de 30s
        // o sistema entende que o token é válido, então emite novos tokens e retorna para o usuário
        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new IllegalArgumentException("Refresh token inválido"));
        // O prazo é herdado: sem isso, a sessão de 30 dias de quem marcou "manter
        // conectado" encolheria para o prazo curto já no primeiro refresh.
        return issueTokens(user, stored.isRememberMe());
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
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        taskServiceClient.deleteUserData(authorizationHeader);
        refreshTokenRepository.deleteByUserId(userId);
        userRepository.delete(user);
    }

    public UserResponse getMe(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));
        return toResponse(user);
    }

    @Transactional
    public UserResponse updateMe(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Usuário não encontrado"));

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }

        if (request.email() != null && !request.email().isBlank()
                && !request.email().trim().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(request.email().trim())) {
                throw new IllegalArgumentException("Email já cadastrado");
            }
            user.setEmail(request.email().trim());
        }

        if (request.newPassword() != null && !request.newPassword().isBlank()) {
            if (request.currentPassword() == null
                    || !passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
                throw new IllegalArgumentException("Senha atual incorreta");
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

    private AuthResponse issueTokens(User user, boolean rememberMe) {
        String accessToken = jwtUtil.generateAccessToken(user.getId(), user.getEmail(), DEFAULT_PROFILE);
        String refreshTokenValue = generateRefreshTokenValue();
        long expirationMs = rememberMe ? refreshTokenRememberExpirationMs : refreshTokenExpirationMs;
        RefreshToken refreshToken = RefreshToken.builder()
                .tokenHash(sha256(refreshTokenValue))
                .userId(user.getId())
                .email(user.getEmail())
                .profile(DEFAULT_PROFILE)
                .rememberMe(rememberMe)
                .expiresAt(LocalDateTime.now().plus(expirationMs, ChronoUnit.MILLIS))
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
