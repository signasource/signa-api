package com.signasource.signa_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.learning.service.SignMediaStorage;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final SignMediaProperties signMediaProperties;

    /**
     * Local fallback for sign animations uploaded without R2 credentials (see SignMediaStorage). In
     * prod the files live in R2 and this directory stays empty.
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        Path root = Path.of(signMediaProperties.localDir()).toAbsolutePath().normalize();
        registry.addResourceHandler(SignMediaStorage.MEDIA_PATH + "/**")
                .addResourceLocations(root.toUri().toString());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(objectMapper, metricsService))
                .addPathPatterns(
                        "/users/username-availability",
                        "/store/gem-orders/**",
                        "/telemetry/events");
        registry.addInterceptor(RateLimitInterceptor.forAuthEmail(objectMapper, metricsService))
                .addPathPatterns(
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/verify-email",
                        "/auth/resend-verification-email");
    }
}
