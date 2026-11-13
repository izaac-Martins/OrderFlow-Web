package com.orderflow.auth.dto;

import com.orderflow.shared.security.Role;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Resposta de autenticação e renovação de token conforme schema {@code TokenResponse} do {@code openapi.yaml}.
 */
public record TokenResponse(
        String accessToken,
        String refreshToken,
        Instant expiraEm,
        UsuarioTokenInfo usuario
) {
    public record UsuarioTokenInfo(
            UUID id,
            String nome,
            String email,
            Role role,
            UUID lojaId,
            List<String> authorities
    ) {
    }
}
