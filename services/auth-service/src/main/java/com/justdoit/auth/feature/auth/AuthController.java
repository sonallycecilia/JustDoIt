package com.justdoit.auth.feature.auth;

import com.justdoit.auth.shared.AuthResponse;
import com.justdoit.auth.shared.CheckEmailResponse;
import com.justdoit.common.web.ErrorResponse;
import com.justdoit.auth.shared.LoginRequest;
import com.justdoit.auth.shared.RefreshRequest;
import com.justdoit.auth.shared.RegisterRequest;
import com.justdoit.auth.shared.UpdateProfileRequest;
import com.justdoit.auth.shared.UserResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final TurnstileService turnstileService;

    @PostMapping("/register")
    public ResponseEntity<?> register(
            @RequestBody @Valid RegisterRequest request,
            @RequestHeader(value = "X-Turnstile-Token", required = false) String turnstileToken) {
        try {
            if (!turnstileService.isValid(turnstileToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Falha na verificação de segurança. Acesso bloqueado."));
            }
            return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
        }
    }

    @GetMapping("/check-email")
    public ResponseEntity<CheckEmailResponse> checkEmail(@RequestParam String email) {
        return ResponseEntity.ok(authService.checkEmail(email));
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
            @RequestBody @Valid LoginRequest request,
            @RequestHeader(value = "X-Turnstile-Token", required = false) String turnstileToken) {
        try {
            if (!turnstileService.isValid(turnstileToken)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN)
                        .body(new ErrorResponse("Falha na verificação de segurança. Acesso bloqueado."));
            }
            return ResponseEntity.ok(authService.login(request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(new ErrorResponse(e.getMessage()));
        }
    }

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody @Valid RefreshRequest request) {
        try {
            return ResponseEntity.ok(authService.refresh(request.refreshToken()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(new ErrorResponse(e.getMessage()));
        }
    }

    // Nos endpoints autenticados, o userId vem do principal que o JwtAuthFilter já
    // validou e colocou no SecurityContext — o controller não re-parseia o token.

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal UUID userId) {
        authService.logout(userId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal UUID userId) {
        return ResponseEntity.ok(authService.getMe(userId));
    }

    @PutMapping("/me")
    public ResponseEntity<?> updateMe(@AuthenticationPrincipal UUID userId,
                                      @RequestBody @Valid UpdateProfileRequest request) {
        try {
            return ResponseEntity.ok(authService.updateMe(userId, request));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        }
    }

    @DeleteMapping("/me")
    public ResponseEntity<?> deleteMe(@AuthenticationPrincipal UUID userId,
                                      @RequestHeader("Authorization") String authHeader) {
        try {
            authService.deleteAccount(userId, authHeader);
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(new ErrorResponse(e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body(new ErrorResponse("Não foi possível remover seus dados de tarefas. Tente novamente."));
        }
    }
}