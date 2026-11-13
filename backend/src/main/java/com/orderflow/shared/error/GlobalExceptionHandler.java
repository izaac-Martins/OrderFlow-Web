package com.orderflow.shared.error;

import com.orderflow.shared.id.UuidV7Generator;
import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Handler global REST que garante que toda resposta {@code >= 400} seja emitida como
 * {@code application/problem+json} respeitando rigorosamente o schema {@code Problem}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    @ExceptionHandler(OrderFlowException.class)
    public ResponseEntity<ProblemResponse> handleOrderFlowException(
            OrderFlowException ex,
            HttpServletRequest request
    ) {
        CodigoErro code = ex.getCodigoErro();
        ProblemResponse body = new ProblemResponse(
                code.getTypeUri(),
                code.getDefaultTitle(),
                code.getHttpStatus().value(),
                code,
                ex.getMessage(),
                request.getRequestURI(),
                resolveTraceId(),
                ex.getErros(),
                ex.getEstadoAtual(),
                ex.getItensIndisponiveis()
        );
        return ResponseEntity
                .status(code.getHttpStatus())
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemResponse> handleValidation(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<ProblemResponse.CampoErro> campos = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ProblemResponse.CampoErro(
                        fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Valor inválido",
                        fe.getCode()
                ))
                .toList();

        return buildSimpleProblem(
                CodigoErro.VALIDACAO_CAMPOS,
                "Um ou mais campos enviados possuem erros de validação.",
                request,
                campos
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemResponse> handleConstraintViolation(
            ConstraintViolationException ex,
            HttpServletRequest request
    ) {
        List<ProblemResponse.CampoErro> campos = ex.getConstraintViolations()
                .stream()
                .map(cv -> new ProblemResponse.CampoErro(
                        cv.getPropertyPath().toString(),
                        cv.getMessage(),
                        cv.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName()
                ))
                .toList();

        return buildSimpleProblem(
                CodigoErro.VALIDACAO_CAMPOS,
                "Parâmetros da requisição inválidos.",
                request,
                campos
        );
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemResponse> handleUnreadableJson(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        return buildSimpleProblem(
                CodigoErro.JSON_INVALIDO,
                "O corpo da requisição não pôde ser interpretado como JSON válido ou contém valores incompatíveis.",
                request,
                Collections.emptyList()
        );
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ProblemResponse> handleMissingHeader(
            MissingRequestHeaderException ex,
            HttpServletRequest request
    ) {
        if ("Idempotency-Key".equalsIgnoreCase(ex.getHeaderName())) {
            return buildSimpleProblem(
                    CodigoErro.IDEMPOTENCY_KEY_AUSENTE,
                    "O header Idempotency-Key (UUID) é obrigatório para esta operação.",
                    request,
                    Collections.emptyList()
            );
        }
        return buildSimpleProblem(
                CodigoErro.VALIDACAO_CAMPOS,
                "Header obrigatório ausente: " + ex.getHeaderName(),
                request,
                List.of(new ProblemResponse.CampoErro(ex.getHeaderName(), "Header obrigatório ausente", "MissingHeader"))
        );
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ProblemResponse> handleOptimisticLock(
            Exception ex,
            HttpServletRequest request
    ) {
        return buildSimpleProblem(
                CodigoErro.VERSAO_CONFLITANTE,
                "O registro foi modificado por outro usuário ou processo. Recarregue os dados e tente novamente.",
                request,
                Collections.emptyList()
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemResponse> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        return buildSimpleProblem(
                CodigoErro.ACESSO_NEGADO,
                "Seu perfil de acesso não possui permissão para executar esta ação.",
                request,
                Collections.emptyList()
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemResponse> handleAuthentication(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        return buildSimpleProblem(
                CodigoErro.NAO_AUTENTICADO,
                "Autenticação inválida ou ausente.",
                request,
                Collections.emptyList()
        );
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemResponse> handleNotFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        return buildSimpleProblem(
                CodigoErro.RECURSO_NAO_ENCONTRADO,
                "Rota ou recurso não encontrado: " + request.getRequestURI(),
                request,
                Collections.emptyList()
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemResponse> handleUnexpected(
            Exception ex,
            HttpServletRequest request
    ) {
        String traceId = resolveTraceId();
        log.error("[traceId={}] Erro inesperado ao processar {} {}", traceId, request.getMethod(), request.getRequestURI(), ex);
        CodigoErro code = CodigoErro.ERRO_INTERNO;
        ProblemResponse body = new ProblemResponse(
                code.getTypeUri(),
                code.getDefaultTitle(),
                code.getHttpStatus().value(),
                code,
                "Ocorreu um erro interno inesperado. Informe o traceId ao suporte.",
                request.getRequestURI(),
                traceId,
                Collections.emptyList(),
                null,
                Collections.emptyList()
        );
        return ResponseEntity
                .status(code.getHttpStatus())
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(body);
    }

    private ResponseEntity<ProblemResponse> buildSimpleProblem(
            CodigoErro code,
            String detail,
            HttpServletRequest request,
            List<ProblemResponse.CampoErro> erros
    ) {
        ProblemResponse body = new ProblemResponse(
                code.getTypeUri(),
                code.getDefaultTitle(),
                code.getHttpStatus().value(),
                code,
                detail,
                request.getRequestURI(),
                resolveTraceId(),
                erros,
                null,
                Collections.emptyList()
        );
        return ResponseEntity
                .status(code.getHttpStatus())
                .header(HttpHeaders.CONTENT_TYPE, PROBLEM_JSON.toString())
                .body(body);
    }

    private String resolveTraceId() {
        String traceId = MDC.get("traceId");
        if (traceId == null || traceId.isBlank()) {
            UUID id = UuidV7Generator.nextId();
            traceId = id.toString().replace("-", "");
        }
        return traceId;
    }
}
