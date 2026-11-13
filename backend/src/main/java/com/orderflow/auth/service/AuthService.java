package com.orderflow.auth.service;

import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.domain.RefreshToken;
import com.orderflow.auth.domain.Usuario;
import com.orderflow.auth.dto.LoginRequest;
import com.orderflow.auth.dto.RefreshRequest;
import com.orderflow.auth.dto.TokenResponse;
import com.orderflow.auth.dto.ValidarPinRequest;
import com.orderflow.auth.dto.ValidarPinResponse;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.repository.RefreshTokenRepository;
import com.orderflow.auth.repository.UsuarioRepository;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.JwtProperties;
import com.orderflow.shared.security.JwtTokenService;
import com.orderflow.shared.security.Role;
import com.orderflow.shared.security.TenantContext;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Casos de uso de Autenticação, Renovação Rotativa de Refresh Token e Validação de PIN de Alçada.
 */
@Service
public class AuthService {

    private static final List<Role> ROLES_ALCADA = List.of(Role.GERENTE, Role.ADMIN);

    private final UsuarioRepository usuarioRepository;
    private final LojaRepository lojaRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final JwtProperties jwtProperties;
    private final TenantContext tenantContext;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UsuarioRepository usuarioRepository,
            LojaRepository lojaRepository,
            RefreshTokenRepository refreshTokenRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenService jwtTokenService,
            JwtProperties jwtProperties,
            TenantContext tenantContext
    ) {
        this.usuarioRepository = usuarioRepository;
        this.lojaRepository = lojaRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.jwtProperties = jwtProperties;
        this.tenantContext = tenantContext;
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        String normalizedEmail = Usuario.normalizeEmail(request.login());
        Usuario usuario = localizarUsuarioParaLogin(normalizedEmail, request.lojaId(), request.codigoLoja());

        if (!usuario.isAtivo() || !passwordEncoder.matches(request.senha(), usuario.getSenhaHash())) {
            throw new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Credenciais inválidas ou usuário inativo.");
        }

        Loja loja = lojaRepository.findByIdAndAtivoTrue(usuario.getLojaId())
                .orElseThrow(() -> new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Loja do usuário está inativa."));

        Instant now = Instant.now();
        usuario.registrarLogin(now);
        usuarioRepository.save(usuario);

        return emitirParDeTokens(usuario, loja.getId(), request.dispositivo(), now);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        String hash = sha256Hex(request.refreshToken().trim());
        RefreshToken tokenAtual = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.NAO_AUTENTICADO, "Refresh token inválido ou não reconhecido."));

        Instant now = Instant.now();
        if (!tokenAtual.estaAtivoEm(now)) {
            throw new OrderFlowException(CodigoErro.TOKEN_EXPIRADO, "Refresh token expirado ou já revogado.");
        }

        // Rotação estrita: revoga o refresh token utilizado e emite um novo par
        tokenAtual.revogar(now);
        refreshTokenRepository.save(tokenAtual);

        Usuario usuario = usuarioRepository.findByIdAndLojaId(tokenAtual.getUsuarioId(), tokenAtual.getLojaId())
                .filter(Usuario::isAtivo)
                .orElseThrow(() -> new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Usuário inativo ou não encontrado."));

        return emitirParDeTokens(usuario, usuario.getLojaId(), tokenAtual.getDispositivo(), now);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        String hash = sha256Hex(request.refreshToken().trim());
        refreshTokenRepository.findByTokenHash(hash).ifPresent(rt -> {
            rt.revogar(Instant.now());
            refreshTokenRepository.save(rt);
        });
    }

    /**
     * Valida o PIN de alçada (4 a 6 dígitos) de um GERENTE ou ADMIN ativo na loja corrente,
     * retornando os dados do aprovador ou lançando {@code 403 PIN_APROVADOR_INVALIDO}.
     */
    @Transactional(readOnly = true)
    public ValidarPinResponse validarPinAlcada(ValidarPinRequest request) {
        UUID lojaId = tenantContext.requireCurrentLojaId();
        return validarPinAlcadaNaLoja(lojaId, request.pin());
    }

    @Transactional(readOnly = true)
    public ValidarPinResponse validarPinAlcadaNaLoja(UUID lojaId, String rawPin) {
        if (rawPin == null || !rawPin.matches("^[0-9]{4,6}$")) {
            throw new OrderFlowException(CodigoErro.PIN_APROVADOR_INVALIDO, "PIN de aprovação gerencial inválido.");
        }

        List<Usuario> aprovadores = usuarioRepository.findByLojaIdAndRoleInAndAtivoTrue(lojaId, ROLES_ALCADA);
        for (Usuario candidato : aprovadores) {
            if (candidato.possuiAlcadaGerencial() && passwordEncoder.matches(rawPin, candidato.getPinAlcadaHash())) {
                return new ValidarPinResponse(
                        candidato.getId(),
                        candidato.getNome(),
                        candidato.getRole(),
                        candidato.getLojaId(),
                        Instant.now()
                );
            }
        }

        throw new OrderFlowException(
                CodigoErro.PIN_APROVADOR_INVALIDO,
                "PIN informado não corresponde a nenhum GERENTE ou ADMIN ativo desta loja."
        );
    }

    private Usuario localizarUsuarioParaLogin(String email, UUID lojaId, String codigoLoja) {
        if (lojaId != null) {
            return usuarioRepository.findByLojaIdAndEmailIgnoreCase(lojaId, email)
                    .orElseThrow(() -> new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Credenciais inválidas."));
        }
        if (codigoLoja != null && !codigoLoja.isBlank()) {
            Loja loja = lojaRepository.findByCodigoIgnoreCaseAndAtivoTrue(codigoLoja.trim())
                    .orElseThrow(() -> new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Credenciais inválidas."));
            return usuarioRepository.findByLojaIdAndEmailIgnoreCase(loja.getId(), email)
                    .orElseThrow(() -> new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Credenciais inválidas."));
        }

        List<Usuario> encontrados = usuarioRepository.findByEmailIgnoreCaseAndAtivoTrue(email);
        if (encontrados.isEmpty()) {
            throw new OrderFlowException(CodigoErro.CREDENCIAIS_INVALIDAS, "Credenciais inválidas.");
        }
        return encontrados.get(0);
    }

    private TokenResponse emitirParDeTokens(Usuario usuario, UUID lojaId, String dispositivo, Instant now) {
        AuthenticatedUser authUser = new AuthenticatedUser(
                usuario.getId(),
                lojaId,
                usuario.getNome(),
                usuario.getRole(),
                Set.copyOf(usuario.getRole().allGrantedAuthorities())
        );

        String accessToken = jwtTokenService.generateAccessToken(authUser, now);
        Instant accessExpiresAt = now.plus(jwtProperties.accessTokenTtl());

        String rawRefreshToken = gerarRefreshTokenOpaco();
        RefreshToken refreshEntity = new RefreshToken(
                lojaId,
                usuario.getId(),
                sha256Hex(rawRefreshToken),
                dispositivo,
                now.plus(jwtProperties.refreshTokenTtl())
        );
        refreshTokenRepository.save(refreshEntity);

        return new TokenResponse(
                accessToken,
                rawRefreshToken,
                accessExpiresAt,
                new TokenResponse.UsuarioTokenInfo(
                        usuario.getId(),
                        usuario.getNome(),
                        usuario.getEmail(),
                        usuario.getRole(),
                        lojaId,
                        usuario.getRole().allGrantedAuthorities()
                )
        );
    }

    private String gerarRefreshTokenOpaco() {
        byte[] randomBytes = new byte[32];
        secureRandom.nextBytes(randomBytes);
        return UuidV7Generator.nextId() + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Algoritmo SHA-256 indisponível na JVM", ex);
        }
    }
}
