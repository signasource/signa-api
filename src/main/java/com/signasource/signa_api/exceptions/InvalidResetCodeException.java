package com.signasource.signa_api.exceptions;

/**
 * Wrong, expired or exhausted password reset code. Always the same message, so the response does
 * not reveal which of those it was (or whether the email has an account).
 */
public class InvalidResetCodeException extends InvalidInputException {
    private static final String DEFAULT_MESSAGE = "Invalid or expired code";

    public InvalidResetCodeException() {
        super(DEFAULT_MESSAGE);
    }
}
