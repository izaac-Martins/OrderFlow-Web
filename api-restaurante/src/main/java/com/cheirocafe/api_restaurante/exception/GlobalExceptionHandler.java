package com.cheirocafe.api_restaurante.exception;

import jakarta.persistence.OptimisticLockException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroPadraoDTO> tratarRecursoNaoEncontrado(
            RecursoNaoEncontradoException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;
        ErroPadraoDTO body = new ErroPadraoDTO(
                Instant.now(),
                status.value(),
                "Recurso não encontrado",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(TransicaoStatusInvalidaException.class)
    public ResponseEntity<ErroPadraoDTO> tratarTransicaoInvalida(
            TransicaoStatusInvalidaException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.valueOf(422);
        ErroPadraoDTO body = new ErroPadraoDTO(
                Instant.now(),
                status.value(),
                "Transição de status inválida",
                ex.getMessage(),
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, OptimisticLockException.class})
    public ResponseEntity<ErroPadraoDTO> tratarConflitoConcorrencia(
            Exception ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.CONFLICT;
        ErroPadraoDTO body = new ErroPadraoDTO(
                Instant.now(),
                status.value(),
                "Conflito de concorrência",
                "Este registro foi atualizado por outro terminal simultaneamente. Sincronize e tente novamente.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroPadraoDTO> tratarErroValidacao(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            erros.put(fieldError.getField(), fieldError.getDefaultMessage());
        }

        ErroPadraoDTO body = new ErroPadraoDTO(
                Instant.now(),
                status.value(),
                "Erro de validação nos dados enviados",
                "Um ou mais campos estão inválidos. Verifique os detalhes.",
                request.getRequestURI(),
                erros
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErroPadraoDTO> tratarJsonInvalido(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        ErroPadraoDTO body = new ErroPadraoDTO(
                Instant.now(),
                status.value(),
                "Corpo da requisição inválido",
                "Não foi possível ler o JSON enviado. Verifique tipos de dados, valores de categoria (BEBIDAS, DOCES, SALGADOS) e status (RECEBIDO, EM_PREPARO, PRONTO, ENTREGUE, CANCELADO).",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroPadraoDTO> tratarErroGeral(
            Exception ex,
            HttpServletRequest request
    ) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        ErroPadraoDTO body = new ErroPadraoDTO(
                Instant.now(),
                status.value(),
                "Erro interno no servidor",
                "Ocorreu um erro inesperado ao processar a requisição.",
                request.getRequestURI(),
                null
        );
        return ResponseEntity.status(status).body(body);
    }
}
