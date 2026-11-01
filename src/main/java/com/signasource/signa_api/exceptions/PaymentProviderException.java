package com.signasource.signa_api.exceptions;

/** The payment provider is not configured or did not answer as expected (HTTP 503). */
public class PaymentProviderException extends RuntimeException {
    public PaymentProviderException(String message) {
        super(message);
    }

    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
