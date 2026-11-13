package com.orderflow;

import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.persistence.UuidBinaryAttributeConverter;
import com.orderflow.shared.security.AuthenticatedUser;
import com.orderflow.shared.security.JwtProperties;
import com.orderflow.shared.security.JwtTokenService;
import com.orderflow.shared.security.Role;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FoundationArchitectureTest {

    @Test
    void deveGerarUuidV7OrdenavelNoTempoEConverterParaBinary16Idempotente() {
        UUID id1 = UuidV7Generator.nextId();
        UUID id2 = UuidV7Generator.nextId();

        assertThat(id1.version()).isEqualTo(7);
        assertThat(id2.version()).isEqualTo(7);
        assertThat(id1.toString()).isLessThanOrEqualTo(id2.toString());

        UuidBinaryAttributeConverter converter = new UuidBinaryAttributeConverter();
        byte[] bytes = converter.convertToDatabaseColumn(id1);
        assertThat(bytes).hasSize(16);

        UUID reconstructed = converter.convertToEntityAttribute(bytes);
        assertThat(reconstructed).isEqualTo(id1);
    }

    @Test
    void deveEmitirEValidarJwtComLojaIdERoleGranular() {
        JwtProperties props = new JwtProperties(
                "orderflow-test-secret-key-with-at-least-32-bytes-long",
                "https://cafeteria.local",
                Duration.ofMinutes(15),
                Duration.ofHours(12)
        );
        JwtTokenService tokenService = new JwtTokenService(props);

        UUID usuarioId = UuidV7Generator.nextId();
        UUID lojaId = UuidV7Generator.nextId();
        AuthenticatedUser user = new AuthenticatedUser(usuarioId, lojaId, "Maria Gerente", Role.GERENTE, Set.of());

        String token = tokenService.generateAccessToken(user, Instant.now());
        AuthenticatedUser parsed = tokenService.parseAndValidate(token);

        assertThat(parsed.usuarioId()).isEqualTo(usuarioId);
        assertThat(parsed.lojaId()).isEqualTo(lojaId);
        assertThat(parsed.role()).isEqualTo(Role.GERENTE);
        assertThat(parsed.authorities()).contains("ROLE_GERENTE", "caixa:conferir", "catalogo:escrever");
    }

    @Test
    void deveConstruirExcecaoRfc7807ComEstadoAtualEItensIndisponiveis() {
        OrderFlowException conflito = OrderFlowException.conflitoEstado(
                CodigoErro.TRANSICAO_CONCORRENTE,
                "Pedido já entrou em preparo",
                "EM_PREPARO",
                3
        );
        assertThat(conflito.getCodigoErro().getHttpStatus().value()).isEqualTo(409);
        assertThat(conflito.getEstadoAtual().status()).isEqualTo("EM_PREPARO");
        assertThat(conflito.getEstadoAtual().versao()).isEqualTo(3);

        UUID itemId = UuidV7Generator.nextId();
        OrderFlowException indisponivel = OrderFlowException.itensIndisponiveis(
                "Leite de aveia esgotado",
                List.of(itemId)
        );
        assertThat(indisponivel.getCodigoErro().getHttpStatus().value()).isEqualTo(422);
        assertThat(indisponivel.getItensIndisponiveis()).containsExactly(itemId);
    }
}
