package com.orderflow.auth.dto;

import com.orderflow.shared.security.Role;

import java.time.Instant;
import java.util.UUID;

public record ValidarPinResponse(
        UUID aprovadorId,
        String aprovadorNome,
        Role role,
        UUID lojaId,
        Instant validadoEm
) {
}
