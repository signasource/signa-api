package com.signasource.signa_api.content.dto.result;

public record ContentImportOutcome(
        String signLanguageCode, String courseCode, ImportResult result) {}
