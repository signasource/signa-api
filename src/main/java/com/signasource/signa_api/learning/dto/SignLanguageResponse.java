package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.SignLanguage;
import java.util.UUID;

public record SignLanguageResponse(UUID id, String code, String name, String countryCode) {
    public static SignLanguageResponse from(SignLanguage signLanguage) {
        return new SignLanguageResponse(
                signLanguage.getId(),
                signLanguage.getCode(),
                signLanguage.getName(),
                signLanguage.getCountryCode());
    }
}
