package com.orderflow.auth.web;

import com.orderflow.auth.dto.LoginRequest;
import com.orderflow.auth.dto.RefreshRequest;
import com.orderflow.auth.dto.TokenResponse;
import com.orderflow.auth.dto.ValidarPinRequest;
import com.orderflow.auth.dto.ValidarPinResponse;
import com.orderflow.auth.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de Autenticação, Refresh Rotativo e Validação de PIN de Alçada ({@code /api/v1/auth}).
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(authService.refresh(request));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/validar-pin")
    public ResponseEntity<ValidarPinResponse> validarPin(@Valid @RequestBody ValidarPinRequest request) {
        return ResponseEntity.ok(authService.validarPinAlcada(request));
    }
}
