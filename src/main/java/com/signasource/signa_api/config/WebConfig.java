package com.signasource.signa_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.common.MetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new RateLimitInterceptor(objectMapper, metricsService))
                .addPathPatterns("/users/username-availability", "/store/gem-purchases");
        registry.addInterceptor(RateLimitInterceptor.forAuthEmail(objectMapper, metricsService))
                .addPathPatterns(
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/resend-verification-email");
    }
}
