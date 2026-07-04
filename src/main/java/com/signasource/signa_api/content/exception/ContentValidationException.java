package com.signasource.signa_api.content.exception;

import java.util.List;

public class ContentValidationException extends ContentLoadException {

    private final List<String> errors;

    public ContentValidationException(List<String> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public List<String> errors() {
        return errors;
    }

    private static String buildMessage(List<String> errors) {
        var sb = new StringBuilder("Content validation failed:\n");
        for (String error : errors) {
            sb.append("  - ").append(error).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
