package com.signasource.signa_api.content.dto.validation;

public record ValidationError(String location, String message) {
    public String render() {
        return location + ": " + message;
    }
}
