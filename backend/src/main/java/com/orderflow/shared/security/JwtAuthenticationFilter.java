package com.orderflow.shared.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.orderflow.shared.error.CodigoErro;
import com.orderflow.shared.error.OrderFlowException;
import com.orderflow.shared.error.ProblemResponse;
import com.orderflow.shared.id.UuidV7Generator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

/**
 * Filtro HTTP que:
 * 1. Injeta um {@code traceId} (UUIDv7) no MDC e no header {@code X-Trace-Id}.
 * 2. Extrai e valida o JWT Bearer, populando {@link AuthenticatedUser} com {@code lojaId} e RBAC.
 * 3. Em caso de token inválido/expirado, responde imediatamente no padrão RFC 7807 Problem Details.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;
    private final ObjectMapper objectMapper;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService, ObjectMapper objectMapper) {
        this.jwtTokenService = jwtTokenService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String traceId = UuidV7Generator.nextId().toString().replace("-", "");
        MDC.put("traceId", traceId);
        response.setHeader("X-Trace-Id", traceId);

        try {
            String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
                String rawToken = authHeader.substring(BEARER_PREFIX.length()).trim();
                if (!rawToken.isEmpty()) {
                    AuthenticatedUser user = jwtTokenService.parseAndValidate(rawToken);
                    var authentication = jwtTokenService.toAuthentication(user);
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                    MDC.put("lojaId", user.lojaId().toString());
                    MDC.put("usuarioId", user.usuarioId().toString());
                }
            }
            filterChain.doFilter(request, response);
        } catch (OrderFlowException ex) {
            SecurityContextHolder.clearContext();
            writeProblemResponse(request, response, ex.getCodigoErro(), ex.getMessage(), traceId);
        } finally {
            MDC.clear();
        }
    }

    private void writeProblemResponse(
            HttpServletRequest request,
            HttpServletResponse response,
            CodigoErro code,
            String detail,
            String traceId
    ) throws IOException {
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
