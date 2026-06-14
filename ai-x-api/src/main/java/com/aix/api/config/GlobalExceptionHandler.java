package com.aix.api.config;

import com.aix.common.config.IngestProperties;
import com.aix.common.exception.AixException;
import com.aix.common.model.ApiCode;
import com.aix.common.model.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AixException.class)
    public ResponseEntity<ApiResponse<Void>> handleAixException(AixException ex) {
        HttpStatus status = switch (ex.getCode()) {
            case INVALID_ARGUMENT -> HttpStatus.BAD_REQUEST;
            case SESSION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case DUPLICATE_MESSAGE -> HttpStatus.OK;
            case UNAUTHORIZED -> HttpStatus.UNAUTHORIZED;
            default -> HttpStatus.INTERNAL_SERVER_ERROR;
        };
        return ResponseEntity.status(status).body(ApiResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(err -> err.getField() + ": " + err.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.badRequest().body(ApiResponse.of(ApiCode.INVALID_ARGUMENT, message));
    }
}
