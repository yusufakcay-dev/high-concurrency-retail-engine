package io.github.yusufakcay_dev.order_service.exception;

import io.github.yusufakcay_dev.order_service.client.InventoryServiceFallback.InventoryServiceUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Global Exception Handler following RFC 7807 Problem Details for HTTP APIs
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Handle Circuit Breaker Open (503 Service Unavailable)
     */
    @ExceptionHandler(CallNotPermittedException.class)
    public ResponseEntity<ProblemDetail> handleCircuitBreakerOpen(
            CallNotPermittedException ex, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service is temporarily unavailable due to high failure rate. Please try again later.");

        problemDetail.setTitle("Service Unavailable - Circuit Breaker Open");
        problemDetail.setType(URI.create("https://api.retail-engine.com/errors/circuit-breaker-open"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("circuitBreaker", ex.getCausingCircuitBreakerName());
        problemDetail.setProperty("retryAfterSeconds", 30);

        log.error("Circuit breaker OPEN for: {}", ex.getCausingCircuitBreakerName());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(problemDetail);
    }

    /**
     * Handle Inventory Service Unavailable (503 Service Unavailable)
     */
    @ExceptionHandler(InventoryServiceUnavailableException.class)
    public ResponseEntity<ProblemDetail> handleInventoryServiceUnavailable(
            InventoryServiceUnavailableException ex, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.SERVICE_UNAVAILABLE,
                ex.getMessage());

        problemDetail.setTitle("Inventory Service Unavailable");
        problemDetail.setType(URI.create("https://api.retail-engine.com/errors/inventory-service-unavailable"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("retryAfterSeconds", 30);

        log.error("Inventory service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header("Retry-After", "30")
                .body(problemDetail);
    }

    /**
     * Handle validation errors (400 Bad Request)
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                "Validation failed for one or more fields");

        problemDetail.setTitle("Validation Error");
        problemDetail.setType(URI.create("https://api.retail-engine.com/errors/validation-error"));
        problemDetail.setProperty("timestamp", Instant.now());
        problemDetail.setProperty("errors", errors);

        log.warn("Validation error: {}", errors);
        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handle IllegalArgumentException (400 Bad Request)
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgumentException(
            IllegalArgumentException ex, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST,
                ex.getMessage());

        problemDetail.setTitle("Bad Request");
        problemDetail.setType(URI.create("https://api.retail-engine.com/errors/bad-request"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.badRequest().body(problemDetail);
    }

    /**
     * Handle RuntimeException (500 Internal Server Error)
     */
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ProblemDetail> handleRuntimeException(
            RuntimeException ex, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                ex.getMessage());

        problemDetail.setTitle("Runtime Error");
        problemDetail.setType(URI.create("https://api.retail-engine.com/errors/runtime-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.error("Runtime exception occurred: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }

    /**
     * Handle all other exceptions (500 Internal Server Error)
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleGenericException(
            Exception ex, WebRequest request) {

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred. Please try again later.");

        problemDetail.setTitle("Internal Server Error");
        problemDetail.setType(URI.create("https://api.retail-engine.com/errors/internal-server-error"));
        problemDetail.setProperty("timestamp", Instant.now());

        log.error("Unexpected error occurred", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(problemDetail);
    }
}
