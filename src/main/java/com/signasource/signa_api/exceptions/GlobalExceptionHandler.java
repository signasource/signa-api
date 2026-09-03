package com.signasource.signa_api.exceptions;

import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
        return ResponseEntity.status(401).body(ErrorResponse.of(ex.getMessage(), 401));
    }

    @ExceptionHandler(InvalidInputException.class)
    public ResponseEntity<ErrorResponse> handleInvalidInput(InvalidInputException ex) {
        return ResponseEntity.status(400).body(ErrorResponse.of(ex.getMessage(), 400));
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(InvalidTokenException ex) {
        return ResponseEntity.status(401).body(ErrorResponse.of(ex.getMessage(), 401));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
        return ResponseEntity.status(404).body(ErrorResponse.of(ex.getMessage(), 404));
    }

    @ExceptionHandler(ResourceAlreadyInUseException.class)
    public ResponseEntity<ErrorResponse> handleResourceAlreadyInUse(
            ResourceAlreadyInUseException ex) {
        return ResponseEntity.status(409).body(ErrorResponse.of(ex.getMessage(), 409));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex) {
        String message =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(err -> err.getField() + ": " + err.getDefaultMessage())
                        .findFirst()
                        .orElse("Validation error");

        return ResponseEntity.status(400).body(ErrorResponse.of(message, 400));
    }

    /** Query param and path variable validation; without this it would answer 500, not 400. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(
            ConstraintViolationException ex) {
        String message =
                ex.getConstraintViolations().stream()
                        .findFirst()
                        .map(
                                violation -> {
                                    String path = violation.getPropertyPath().toString();
                                    String field = path.substring(path.lastIndexOf('.') + 1);
                                    return field + ": " + violation.getMessage();
                                })
                        .orElse("Validation error");

        return ResponseEntity.status(400).body(ErrorResponse.of(message, 400));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleBadCredentials(AuthenticationException ex) {
        log.error("Authentication error", ex);
        return ResponseEntity.status(401).body(ErrorResponse.of("Invalid credentials", 401));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(500).body(ErrorResponse.of("Internal server error", 500));
    }
}
