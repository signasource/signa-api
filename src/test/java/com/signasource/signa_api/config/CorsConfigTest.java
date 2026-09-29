package com.signasource.signa_api.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

class CorsConfigTest {

    private final CorsConfig corsConfig = new CorsConfig();

    private CorsConfiguration configurationFor(List<String> origins) {
        CorsConfigurationSource source = corsConfig.corsConfigurationSource(origins);
        HttpServletRequest request = new MockHttpServletRequest("GET", "/organizations/me");
        return source.getCorsConfiguration(request);
    }

    @Test
    void shouldSendNoCorsConfigurationWhenNoOriginIsConfigured() {
        assertNull(configurationFor(List.of()));
    }

    @Test
    void shouldIgnoreBlankOrigins() {
        assertNull(configurationFor(List.of("", "  ")));
    }

    @Test
    void shouldAllowOnlyTheConfiguredOriginsWithoutCredentials() {
        CorsConfiguration configuration =
                configurationFor(List.of("https://panel.signa.app", "http://localhost:5173"));

        assertNotNull(configuration);
        assertEquals(
                List.of("https://panel.signa.app", "http://localhost:5173"),
                configuration.getAllowedOrigins());
        assertTrue(configuration.getAllowedMethods().contains("PATCH"));
        assertTrue(configuration.getAllowedHeaders().contains("Authorization"));
        assertFalse(Boolean.TRUE.equals(configuration.getAllowCredentials()));
    }
}
