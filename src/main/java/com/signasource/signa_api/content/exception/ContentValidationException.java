package com.signasource.signa_api.content.exception;

import com.signasource.signa_api.content.dto.validation.ValidationError;
import java.util.List;

public class ContentValidationException extends ContentLoadException {

    private final transient List<ValidationError> errors;

    public ContentValidationException(List<ValidationError> errors) {
        super(buildMessage(errors));
        this.errors = List.copyOf(errors);
    }

    public List<ValidationError> errors() {
        return errors;
    }

    private static String buildMessage(List<ValidationError> errors) {
        var sb = new StringBuilder("Content validation failed:\n");
        for (ValidationError error : errors) {
            sb.append("  - ").append(error.render()).append("\n");
        }
        return sb.toString().stripTrailing();
    }
}
