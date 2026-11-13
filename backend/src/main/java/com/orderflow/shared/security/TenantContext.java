package com.orderflow.shared.security;

import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

/**
 * Acesso seguro ao contexto do tenant ({@code lojaId}) e do usuário autenticado
 * a partir do {@link SecurityContextHolder}.
 */
@Component
public class TenantContext {

    public Optional<AuthenticatedUser> currentUserOptional() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return Optional.empty();
        }
        if (authentication.getPrincipal() instanceof AuthenticatedUser user) {
            return Optional.of(user);
        }
        return Optional.empty();
    }

    public AuthenticatedUser requireCurrentUser() {
        return currentUserOptional()
                .orElseThrow(() -> new OrderFlowException(CodigoErro.NAO_AUTENTICADO, "Usuário não autenticado no contexto atual."));
    }

    public UUID requireCurrentLojaId() {
        return requireCurrentUser().lojaId();
    }

    public UUID requireCurrentUsuarioId() {
        return requireCurrentUser().usuarioId();
    }
}
