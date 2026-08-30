package com.signasource.signa_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "r2")
public record R2Properties(
        String endpoint,
        String accessKeyId,
        String secretAccessKey,
        String bucket,
        long presignExpiryMinutes) {}
