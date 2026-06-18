package com.signasource.signa_api.exceptions;

public class InvalidTokenException extends RuntimeException {
	private static final String DEFAULT_MESSAGE = "Invalid or expired token";

	public InvalidTokenException(String message) {
		super(message);
	}

	public InvalidTokenException() {
		this(DEFAULT_MESSAGE);
	}
}
