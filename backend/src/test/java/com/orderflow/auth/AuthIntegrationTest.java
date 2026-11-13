package com.orderflow.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.auth.domain.Loja;
import com.orderflow.auth.domain.Usuario;
import com.orderflow.auth.dto.LoginRequest;
import com.orderflow.auth.dto.RefreshRequest;
import com.orderflow.auth.dto.TokenResponse;
import com.orderflow.auth.dto.ValidarPinRequest;
import com.orderflow.auth.repository.LojaRepository;
import com.orderflow.auth.repository.RefreshTokenRepository;
import com.orderflow.auth.repository.UsuarioRepository;
import com.orderflow.shared.id.UuidV7Generator;
import com.orderflow.shared.security.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private LojaRepository lojaRepository;

    @Autowired
    private UsuarioRepository usuarioRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private Loja lojaMatriz;
    private Loja lojaFilial;
    private Usuario atendenteMatriz;
    private Usuario gerenteMatriz;
    private Usuario gerenteFilial;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        usuarioRepository.deleteAll();
        lojaRepository.deleteAll();

        lojaMatriz = lojaRepository.save(new Loja(UuidV7Generator.nextId(), "LOJA-01", "OrderFlow Matriz", "12.345.678/0001-90"));
        lojaFilial = lojaRepository.save(new Loja(UuidV7Generator.nextId(), "LOJA-02", "OrderFlow Filial", "12.345.678/0002-71"));

        atendenteMatriz = usuarioRepository.save(new Usuario(
                lojaMatriz.getId(),
                "Carlos Atendente",
                "carlos@orderflow.local",
                passwordEncoder.encode("Senha@123"),
                Role.ATENDENTE,
                null
        ));

        gerenteMatriz = usuarioRepository.save(new Usuario(
                lojaMatriz.getId(),
                "Marina Gerente",
                "marina@orderflow.local",
                passwordEncoder.encode("Gerente@123"),
                Role.GERENTE,
                passwordEncoder.encode("4826")
        ));

        gerenteFilial = usuarioRepository.save(new Usuario(
                lojaFilial.getId(),
                "Roberto Filial",
                "roberto@orderflow.local",
                passwordEncoder.encode("Filial@123"),
                Role.GERENTE,
                passwordEncoder.encode("9911")
        ));
    }

    @Test
    void deveAutenticarComSucessoEmitirJwtComLojaIdEPermissoesERotacionarRefreshToken() throws Exception {
        LoginRequest loginReq = new LoginRequest(
                "carlos@orderflow.local",
                "Senha@123",
                "TABLET-BALCAO-01",
                lojaMatriz.getId(),
                null
        );

        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.usuario.id").value(atendenteMatriz.getId().toString()))
                .andExpect(jsonPath("$.usuario.lojaId").value(lojaMatriz.getId().toString()))
                .andExpect(jsonPath("$.usuario.role").value("ATENDENTE"))
                .andReturn();

        TokenResponse tokens1 = objectMapper.readValue(loginResult.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(tokens1.usuario().authorities()).contains("ROLE_ATENDENTE", "pedido:criar", "caixa:operar");

        // Renova com refresh token -> deve emitir novo par e revogar o refresh anterior
        RefreshRequest refreshReq = new RefreshRequest(tokens1.refreshToken());
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andReturn();

        TokenResponse tokens2 = objectMapper.readValue(refreshResult.getResponse().getContentAsString(), TokenResponse.class);
        assertThat(tokens2.refreshToken()).isNotEqualTo(tokens1.refreshToken());

        // Tentar reutilizar o refresh token antigo (já rotacionado) deve falhar com 401 TOKEN_EXPIRADO
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(refreshReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRADO"));
    }

    @Test
    void deveRecusarCredenciaisInvalidasComProblemDetails401() throws Exception {
        LoginRequest senhaIncorreta = new LoginRequest(
                "carlos@orderflow.local",
                "SenhaErrada999",
                "TABLET-BALCAO-01",
                lojaMatriz.getId(),
                null
        );

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(senhaIncorreta)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("CREDENCIAIS_INVALIDAS"))
                .andExpect(jsonPath("$.traceId").isNotEmpty());
    }

    @Test
    void deveValidarPinDeAlcadaDaMesmaLojaERecusarPinDeOutraLojaOuInvalidoCom403() throws Exception {
        // 1. Atendente faz login na Loja Matriz
        LoginRequest loginReq = new LoginRequest(
                "carlos@orderflow.local",
                "Senha@123",
                "PDV-CAIXA-01",
                lojaMatriz.getId(),
                null
        );
        String body = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        TokenResponse atendenteToken = objectMapper.readValue(body, TokenResponse.class);

        // 2. Valida o PIN da gerente Marina (4826) da mesma loja -> 200 OK
        ValidarPinRequest pinValido = new ValidarPinRequest("4826", "SANGRIA_ACIMA_LIMITE");
        mockMvc.perform(post("/api/v1/auth/validar-pin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + atendenteToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pinValido)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aprovadorId").value(gerenteMatriz.getId().toString()))
                .andExpect(jsonPath("$.aprovadorNome").value("Marina Gerente"))
                .andExpect(jsonPath("$.role").value("GERENTE"))
                .andExpect(jsonPath("$.lojaId").value(lojaMatriz.getId().toString()));

        // 3. Tenta usar o PIN do gerente Roberto da Filial (9911) na sessão da Matriz -> 403 PIN_APROVADOR_INVALIDO
        ValidarPinRequest pinOutraLoja = new ValidarPinRequest("9911", "SANGRIA_ACIMA_LIMITE");
        mockMvc.perform(post("/api/v1/auth/validar-pin")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + atendenteToken.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pinOutraLoja)))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.code").value("PIN_APROVADOR_INVALIDO"));

        // 4. Sem token Bearer -> 401 NAO_AUTENTICADO
        mockMvc.perform(post("/api/v1/auth/validar-pin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pinValido)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("NAO_AUTENTICADO"));
    }
}
