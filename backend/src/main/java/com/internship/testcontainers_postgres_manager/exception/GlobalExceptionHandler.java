package com.internship.testcontainers_postgres_manager.exception;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.ErrorResponse;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.internship.testcontainers_postgres_manager.dto.ApiErrorResponse;

import jakarta.servlet.http.HttpServletRequest;

/**
 * One central place that turns every failure into the contract's error JSON.
 * Controllers and services just throw; they never build error responses.
 */

@RestControllerAdvice
public class GlobalExceptionHandler 
{
private static final Logger log=LoggerFactory.getLogger(GlobalExceptionHandler.class);
// Our own errors: limit reached, Docker failure, (later) sandbox not found...
@ExceptionHandler(ApiException.class)
public ResponseEntity<ApiErrorResponse> handleApiException(
        ApiException ex, HttpServletRequest request) {
    if (ex.getStatus().is5xxServerError()) {
        log.error("Server error: {}", ex.getMessage(), ex);
    }
    return build(ex.getStatus().value(), ex.getCode(), ex.getMessage(), request);
}

// @Valid failed: for example a blank name
@ExceptionHandler(MethodArgumentNotValidException.class)
public ResponseEntity<ApiErrorResponse> handleValidation(
        MethodArgumentNotValidException ex, HttpServletRequest request) {
    String message = ex.getBindingResult().getFieldErrors().stream()
            .map(FieldError::getDefaultMessage)
            .collect(Collectors.joining("; "));
    return build(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR", message, request);
}

// Body missing or not valid JSON
@ExceptionHandler(HttpMessageNotReadableException.class)
public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
        HttpMessageNotReadableException ex, HttpServletRequest request) {
    return build(HttpStatus.BAD_REQUEST.value(), "VALIDATION_ERROR",
            "Request body is missing or is not valid JSON", request);
}

// Safety net for everything else
@ExceptionHandler(Exception.class)
public ResponseEntity<ApiErrorResponse> handleUnexpected(
        Exception ex, HttpServletRequest request) {

    // Spring's own web errors (wrong URL = 404, wrong method = 405) must keep their status
    if (ex instanceof ErrorResponse springError) {
        int status = springError.getStatusCode().value();
        HttpStatus known = HttpStatus.resolve(status);
        String code = (known != null) ? known.name() : "HTTP_ERROR";
        String detail = springError.getBody().getDetail();
        return build(status, code, detail != null ? detail : "Request could not be processed", request);
    }

    log.error("Unexpected error", ex);
    // Generic message: internal details are never sent to the client
    return build(HttpStatus.INTERNAL_SERVER_ERROR.value(), "INTERNAL_ERROR",
            "An unexpected error occurred", request);
}

// One place that builds the contract's error JSON
private ResponseEntity<ApiErrorResponse> build(
        int status, String code, String message, HttpServletRequest request) {
    ApiErrorResponse body = new ApiErrorResponse(
            Instant.now().truncatedTo(ChronoUnit.SECONDS),
            status, code, message, request.getRequestURI());
    return ResponseEntity.status(status).body(body);
}
}
