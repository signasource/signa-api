package com.signasource.signa_api.content.exception;

public class ContentParseException extends ContentLoadException {

    public ContentParseException(String resourceDescription, Class<?> targetType, Throwable cause) {
        super(
                "Failed to parse " + resourceDescription + " as " + targetType.getSimpleName(),
                cause);
    }
}
