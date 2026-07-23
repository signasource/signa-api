package com.signasource.signa_api.content.dto.config;

import java.util.List;

public record VisualRecognitionConfig(
        List<String> signSequence, List<String> options, Boolean keepOrder) {}
