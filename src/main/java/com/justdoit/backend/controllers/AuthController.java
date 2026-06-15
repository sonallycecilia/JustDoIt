package com.justdoit.backend.controllers;

import com.justdoit.backend.dtos.LoginResponseDTO;
import com.justdoit.backend.dtos.UserLoginRequestDTO;
import com.justdoit.backend.dtos.UserRegisterRequestDTO;
import com.justdoit.backend.dtos.UserResponseDTO;
import com.justdoit.backend.models.User;
import com.justdoit.backend.services.AuthService;
import com.justdoit.backend.services.TokenService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final TokenService tokenService;

    public AuthController(AuthService authService, TokenService tokenService) {
        this.authService = authService;
        this.tokenService = tokenService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponseDTO> register(@Valid @RequestBody UserRegisterRequestDTO request) {
        User createdUser = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserResponseDTO.from(createdUser));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponseDTO> login(@Valid @RequestBody UserLoginRequestDTO request) {
        User authenticatedUser = authService.authenticate(request);
        String token = tokenService.generateToken(authenticatedUser);
        return ResponseEntity.ok(new LoginResponseDTO(token));
    }
}
