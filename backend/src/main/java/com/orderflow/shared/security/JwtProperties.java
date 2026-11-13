package com.orderflow.shared.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "orderflow.security.jwt")
public record JwtProperties(
        String secret,
        String issuer,
        Duration accessTokenTtl,
        Duration refreshTokenTtl
) {
    public JwtProperties {
        if (secret == null || secret.length() < 32) {
            secret = "orderflow-local-lan-hs256-secret-key-minimum-32-bytes-2026";
        }
        if (issuer == null || issuer.isBlank()) {
            issuer = "https://cafeteria.local";
        }
        if (accessTokenTtl == null) {
            accessTokenTtl = Duration.ofMinutes(15);
        }
        if (refreshTokenTtl == null) {
            refreshTokenTtl = Duration.ofHours(12);
        }
    }
}
