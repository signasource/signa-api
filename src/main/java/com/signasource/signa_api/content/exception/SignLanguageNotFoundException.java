package com.signasource.signa_api.content.exception;

public class SignLanguageNotFoundException extends ContentLoadException {

    public SignLanguageNotFoundException(String code) {
        super("Sign language not found: " + code);
    }
}
