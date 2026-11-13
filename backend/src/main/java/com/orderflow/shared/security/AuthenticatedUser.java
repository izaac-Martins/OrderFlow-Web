package com.orderflow.shared.security;

import java.security.Principal;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Principal autenticado imutável carregado a partir do JWT em requisições HTTP e sessões STOMP.
 * Garante isolamento multi-tenant fornecendo {@code lojaId} tipado em {@link UUID}.
 */
public record AuthenticatedUser(
        UUID usuarioId,
        UUID lojaId,
        String nome,
        Role role,
        Set<String> authorities
) implements Principal {

    public AuthenticatedUser {
        Objects.requireNonNull(usuarioId, "usuarioId é obrigatório");
        Objects.requireNonNull(lojaId, "lojaId é obrigatório");
        Objects.requireNonNull(role, "role é obrigatória");
        authorities = authorities != null ? Set.copyOf(authorities) : Set.of();
    }

    @Override
    public String getName() {
        return usuarioId.toString();
    }

    public boolean hasRole(Role expected) {
        return this.role == expected;
    }

    public boolean isGerenteOrAdmin() {
        return this.role == Role.GERENTE || this.role == Role.ADMIN;
    }
}
