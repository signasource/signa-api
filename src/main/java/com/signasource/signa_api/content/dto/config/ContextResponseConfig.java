package com.signasource.signa_api.content.dto.config;

import java.util.List;

public record ContextResponseConfig(String question, String answer, List<String> options) {}
