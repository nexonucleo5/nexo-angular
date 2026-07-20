package com.nexo.api;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Converte exceções no envelope de erro padrão consumido pelo interceptor do Angular:
 * { "timestamp": "...", "status": 400, "error": "VALIDATION_ERROR", "message": "...", "fields": { ... } }
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    public record ErroPadrao(Instant timestamp, int status, String error, String message, Map<String, String> fields) {}

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErroPadrao> handleApi(ApiException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(new ErroPadrao(Instant.now(), ex.getStatus().value(), ex.getError(), ex.getMessage(), ex.getFields()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroPadrao> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErroPadrao(Instant.now(), 400, "VALIDATION_ERROR", "Dados inválidos.", fields));
    }

    @ExceptionHandler(AuthorizationDeniedException.class)
    public ResponseEntity<ErroPadrao> handleForbidden(AuthorizationDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(new ErroPadrao(Instant.now(), 403, "FORBIDDEN", "Acesso negado para o seu perfil.", null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroPadrao> handleGeneric(Exception ex) {
        log.error("Erro interno não tratado", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ErroPadrao(Instant.now(), 500, "INTERNAL_ERROR", "Erro interno inesperado.", null));
    }
}
