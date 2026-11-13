package com.orderflow.shared.security;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.OctetSequenceKey;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Serviço de emissão, validação e conversão de tokens JWT (HMAC-SHA256 local / on-premise)
 * usando Nimbus JOSE + JWT. Funciona 100% offline na LAN da cafeteria.
 */
@Service
public class JwtTokenService {

    private final JwtProperties properties;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtTokenService(JwtProperties properties) {
        this.properties = properties;
        byte[] keyBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        SecretKey secretKey = new SecretKeySpec(keyBytes, "HmacSHA256");
        OctetSequenceKey jwk = new OctetSequenceKey.Builder(keyBytes)
                .algorithm(JWSAlgorithm.HS256)
                .build();
        this.jwtEncoder = new NimbusJwtEncoder((jwkSelector, securityContext) -> List.of(jwk));
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(secretKey)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }

    public String generateAccessToken(AuthenticatedUser user, Instant now) {
        Instant expiresAt = now.plus(properties.accessTokenTtl());
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.issuer())
                .issuedAt(now)
                .expiresAt(expiresAt)
                .subject(user.usuarioId().toString())
                .claim("lojaId", user.lojaId().toString())
                .claim("nome", user.nome())
                .claim("role", user.role().name())
                .claim("authorities", user.role().allGrantedAuthorities())
                .claim("permissions", user.role().getDefaultAuthorities())
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public AuthenticatedUser parseAndValidate(String rawToken) {
        try {
            Jwt jwt = jwtDecoder.decode(rawToken);
            return fromJwt(jwt);
        } catch (JwtValidationException ex) {
            boolean expired = ex.getErrors().stream()
                    .anyMatch(err -> err.getDescription() != null && err.getDescription().toLowerCase().contains("expired"));
            if (expired) {
                throw new OrderFlowException(CodigoErro.TOKEN_EXPIRADO, "O access token expirou.");
            }
            throw new OrderFlowException(CodigoErro.NAO_AUTENTICADO, "Token JWT inválido.");
        } catch (JwtException | IllegalArgumentException ex) {
            throw new OrderFlowException(CodigoErro.NAO_AUTENTICADO, "Token JWT malformado ou assinatura inválida.");
        }
    }

    public UsernamePasswordAuthenticationToken toAuthentication(AuthenticatedUser user) {
        Collection<? extends GrantedAuthority> granted = user.authorities().stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
        return new UsernamePasswordAuthenticationToken(user, null, granted);
    }

    public AuthenticatedUser fromJwt(Jwt jwt) {
        UUID usuarioId = UUID.fromString(jwt.getSubject());
        String lojaIdClaim = jwt.getClaimAsString("lojaId");
        if (lojaIdClaim == null || lojaIdClaim.isBlank()) {
            throw new OrderFlowException(CodigoErro.NAO_AUTENTICADO, "Claim obrigatória 'lojaId' ausente no token JWT.");
        }
        UUID lojaId = UUID.fromString(lojaIdClaim);
        String roleClaim = jwt.getClaimAsString("role");
        Role role = Role.valueOf(roleClaim);
        String nome = jwt.getClaimAsString("nome");

        Set<String> authorities = new HashSet<>(role.allGrantedAuthorities());
        List<String> claimAuthorities = jwt.getClaimAsStringList("authorities");
        if (claimAuthorities != null) {
            authorities.addAll(claimAuthorities);
        }
        List<String> claimPermissions = jwt.getClaimAsStringList("permissions");
        if (claimPermissions != null) {
            authorities.addAll(claimPermissions);
        }

        return new AuthenticatedUser(usuarioId, lojaId, nome != null ? nome : "", role, authorities);
    }
}
