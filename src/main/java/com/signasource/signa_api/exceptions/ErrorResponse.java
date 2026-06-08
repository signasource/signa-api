package com.signasource.signa_api.exceptions;

public record ErrorResponse(String message, int status, long timestamp) {
	public static ErrorResponse of(String message, int status) {
		return new ErrorResponse(message, status, System.currentTimeMillis());
	}
}
