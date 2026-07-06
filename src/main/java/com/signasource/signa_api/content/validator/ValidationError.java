package com.signasource.signa_api.content.validator;

/**
 * A single content validation error. {@code location} identifies where the problem is (e.g. {@code
 * "Topic greetings > Lesson intro > Block #2"}); {@code message} states what is wrong. Used
 * uniformly by structural, block-level and semantic validation.
 */
public record ValidationError(String location, String message) {

    /** Human-readable {@code "location: message"} form (message only when there is no location). */
    public String render() {
        return location == null || location.isBlank() ? message : location + ": " + message;
    }
}
