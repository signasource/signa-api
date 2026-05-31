package com.signasource.signa_api.exceptions;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
	@ExceptionHandler(InvalidCredentialsException.class)
	public ResponseEntity<ErrorResponse> handleInvalidCredentials(InvalidCredentialsException ex) {
		return ResponseEntity.status(401).body(ErrorResponse.of(ex.getMessage(), 401));
	}

	@ExceptionHandler(NotFoundException.class)
	public ResponseEntity<ErrorResponse> handleNotFoundException(NotFoundException ex) {
		return ResponseEntity.status(404).body(ErrorResponse.of(ex.getMessage(), 404));
	}

	@ExceptionHandler(ResourceAlreadyInUse.class)
	public ResponseEntity<ErrorResponse> handleResourceAlreadyInUse(ResourceAlreadyInUse ex) {
		return ResponseEntity.status(409).body(ErrorResponse.of(ex.getMessage(), 409));
	}

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ErrorResponse> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
		String message = ex.getBindingResult().getFieldErrors().stream()
				.map(err -> err.getField() + ": " + err.getDefaultMessage()).findFirst().orElse("Validation error");

		return ResponseEntity.status(400).body(ErrorResponse.of(message, 400));
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ErrorResponse> handleException(Exception ex) {
		return ResponseEntity.status(500).body(ErrorResponse.of(ex.getMessage(), 500));
	}
}
