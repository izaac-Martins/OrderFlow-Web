package com.orderflow.shared.persistence;

import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.TenantContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.UUID;

/**
 * Habilita JPA Auditing populando automaticamente {@code created_by} e {@code updated_by}
 * com o {@code usuarioId} (UUIDv7) extraído do JWT.
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<UUID> auditorProvider(TenantContext tenantContext) {
        return () -> tenantContext.currentUserOptional().map(AuthenticatedUser::usuarioId);
    }
}
