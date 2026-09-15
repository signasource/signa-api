package com.signasource.signa_api.content.dto.config;

import java.util.List;

public record PerformSignConfig(List<String> signs, Double threshold) {}
