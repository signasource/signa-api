package com.signasource.signa_api.exceptions;

public class ResourceAlreadyInUseException extends RuntimeException {
    public ResourceAlreadyInUseException(String message) {
        super(message);
    }
}
