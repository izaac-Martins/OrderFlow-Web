package com.orderflow.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.ProblemResponse;
import com.orderflow.shared.id.UuidV7Generator;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * Configuração de segurança HTTP stateless com RBAC granular e respostas 401/403
 * padronizadas em RFC 7807 ({@code application/problem+json}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter, ObjectMapper objectMapper) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(eh -> eh
                        .authenticationEntryPoint((req, res, ex) ->
                                writeProblem(req, res, CodigoErro.NAO_AUTENTICADO, "Token de acesso ausente ou inválido."))
                        .accessDeniedHandler((req, res, ex) ->
                                writeProblem(req, res, CodigoErro.ACESSO_NEGADO, "Seu perfil não possui permissão para este recurso."))
                )
                .authorizeHttpRequests(auth -> auth
                        // Endpoints públicos de autenticação, bridge de sincronia em tempo real, documentação e handshake WebSocket
                        .requestMatchers("/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/bridge/**", "/h2-console/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/validar-pin")
                        .hasAnyRole("ATENDENTE", "COZINHA", "GERENTE", "ADMIN")
                        .requestMatchers("/ws/**").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/openapi.yaml").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()

                        // Rotas de leitura do catálogo
                        .requestMatchers(HttpMethod.GET, "/api/v1/catalogo", "/api/v1/catalogo/**",
                                "/api/v1/categorias/**", "/api/v1/produtos/**",
                                "/api/v1/grupos-modificadores/**", "/api/v1/insumos/**")
                        .hasAnyRole("ATENDENTE", "COZINHA", "GERENTE", "ADMIN")

                        // Disponibilidade de insumos: COZINHA, GERENTE e ADMIN (Decisão D7)
                        .requestMatchers(HttpMethod.PATCH,
                                "/api/v1/insumos/*/disponibilidade",
                                "/api/v1/catalogo/insumos/*/disponibilidade")
                        .hasAnyRole("COZINHA", "GERENTE", "ADMIN")

                        // Escrita no catálogo e histórico de preços: GERENTE e ADMIN
                        .requestMatchers("/api/v1/catalogo/**", "/api/v1/categorias/**", "/api/v1/produtos/**",
                                "/api/v1/grupos-modificadores/**", "/api/v1/modificadores/**", "/api/v1/insumos/**")
                        .hasAnyRole("GERENTE", "ADMIN")

                        // Rotas do KDS / Cozinha
                        .requestMatchers(HttpMethod.POST, "/api/v1/cozinha/pedidos/*/reimprimir")
                        .hasAnyRole("ATENDENTE", "COZINHA", "GERENTE", "ADMIN")
                        .requestMatchers("/api/v1/cozinha/**")
                        .hasAnyRole("COZINHA", "GERENTE", "ADMIN")

                        // Entrega de pedido: ATENDENTE, COZINHA, GERENTE, ADMIN
                        .requestMatchers(HttpMethod.POST, "/api/v1/pedidos/*/entregar")
                        .hasAnyRole("ATENDENTE", "COZINHA", "GERENTE", "ADMIN")

                        // Demais rotas de pedidos e pagamentos
                        .requestMatchers(HttpMethod.POST, "/api/v1/pagamentos/*/estorno")
                        .hasAnyRole("GERENTE", "ADMIN")
                        .requestMatchers("/api/v1/pedidos/**", "/api/v1/pagamentos/**")
                        .hasAnyRole("ATENDENTE", "GERENTE", "ADMIN")

                        // Rotas gerenciais de caixa (conferência, saldo esperado, extrato, estorno, abertura de gaveta)
                        .requestMatchers(
                                "/api/v1/caixas/*/abrir-gaveta",
                                "/api/v1/caixa/sessoes/*/conferencia",
                                "/api/v1/caixa/sessoes/*/conferir",
                                "/api/v1/caixa/sessoes/*/saldo",
                                "/api/v1/caixa/sessoes/*/movimentacoes",
                                "/api/v1/caixa/movimentacoes/*/estorno"
                        ).hasAnyRole("GERENTE", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/caixa/sessoes")
                        .hasAnyRole("GERENTE", "ADMIN")

                        // Operação de caixa pelo operador (abrir, sangria, suprimento, fechar cego, sessão atual)
                        .requestMatchers("/api/v1/caixas/**", "/api/v1/caixa/**")
                        .hasAnyRole("ATENDENTE", "GERENTE", "ADMIN")

                        // Relatórios executivos
                        .requestMatchers("/api/v1/relatorios/**")
                        .hasAnyRole("GERENTE", "ADMIN")

                        .anyRequest().authenticated()
                )
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("http://localhost:*", "https://*.local", "http://192.168.*.*:*", "http://10.*.*.*:*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key", "If-None-Match", "If-Match", "X-Loja-Id"));
        config.setExposedHeaders(List.of("ETag", "Location", "X-Trace-Id"));
        config.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private void writeProblem(
            HttpServletRequest request,
            HttpServletResponse response,
            CodigoErro code,
            String detail
    ) throws IOException {
        String traceId = MDC.get("traceId");
        if (traceId == null) {
            traceId = UuidV7Generator.nextId().toString().replace("-", "");
        }
        ProblemResponse problem = new ProblemResponse(
                code.getTypeUri(),
                code.getDefaultTitle(),
                code.getHttpStatus().value(),
                code,
                detail,
                request.getRequestURI(),
                traceId,
                Collections.emptyList(),
                null,
                Collections.emptyList()
        );
        response.setStatus(code.getHttpStatus().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getWriter(), problem);
    }
}
