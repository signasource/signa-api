package com.signasource.signa_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Cloudflare R2 (S3-compatible) settings bound from the {@code r2.*} properties. */
@ConfigurationProperties(prefix = "r2")
public record R2Properties(
        String endpoint,
        String accessKeyId,
        String secretAccessKey,
        String bucket,
        long presignExpiryMinutes) {}
