package com.nexo.api;

import org.springframework.http.HttpStatus;

import java.util.Map;

/** Exceção de negócio mapeada para o envelope de erro padrão. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String error;
    private final Map<String, String> fields;

    public ApiException(HttpStatus status, String error, String message) {
        this(status, error, message, null);
    }

    public ApiException(HttpStatus status, String error, String message, Map<String, String> fields) {
        super(message);
        this.status = status;
        this.error = error;
        this.fields = fields;
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", message);
    }

    public static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message);
    }

    public static ApiException validation(String message, Map<String, String> fields) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", message, fields);
    }

    public static ApiException unauthorized(String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", message);
    }

    public HttpStatus getStatus() { return status; }
    public String getError() { return error; }
    public Map<String, String> getFields() { return fields; }
}
