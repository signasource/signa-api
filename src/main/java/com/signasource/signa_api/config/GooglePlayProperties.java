package com.signasource.signa_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Google Play Developer API access, used to verify in-app purchases server-side.
 *
 * <p>{@code credentialsJson} is the base64-encoded service-account JSON (same convention as the
 * Firebase credentials). {@code enabled=false} swaps the real verifier for one that trusts every
 * token, which is only acceptable in local development.
 */
@ConfigurationProperties(prefix = "google-play")
public record GooglePlayProperties(boolean enabled, String packageName, String credentialsJson) {}
