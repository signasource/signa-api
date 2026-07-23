package com.signasource.signa_api.content.exception;

public class ContentLoadException extends RuntimeException {

    public ContentLoadException(String message) {
        super(message);
    }

    public ContentLoadException(String message, Throwable cause) {
        super(message, cause);
    }
}
