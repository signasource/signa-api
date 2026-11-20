package com.signasource.signa_api.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Where sign animations (.glb / .mp4) live and how clients reach them.
 *
 * @param publicBaseUrl public origin of the R2 bucket (the app and the web already read the default
 *     .glb files from it); a key {@code lsa/hola.glb} is served at {@code
 *     <publicBaseUrl>/lsa/hola.glb}
 * @param localDir fallback directory used when R2 credentials are not configured (local/demo):
 *     uploads are written here and served by the API under {@code /media/**}
 */
@ConfigurationProperties(prefix = "sign-media")
public record SignMediaProperties(String publicBaseUrl, String localDir) {}
