package com.signasource.signa_api.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.exceptions.ErrorResponse;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.HandlerInterceptor;

public class RateLimitInterceptor implements HandlerInterceptor {

    static final int BUCKET_CAPACITY = 30;
    static final Duration REFILL_DURATION = Duration.ofMinutes(1);
    static final int AUTH_BUCKET_CAPACITY = 10;
    static final Duration AUTH_REFILL_DURATION = Duration.ofMinutes(15);
    static final int TOO_MANY_REQUESTS = 429;
    private static final int TOKENS_PER_REQUEST = 1;
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";
    private static final String TOO_MANY_REQUESTS_MESSAGE = "Too many requests";

    private final ObjectMapper objectMapper;
    private final MetricsService metricsService;
    private final int capacity;
    private final Duration refillDuration;
    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitInterceptor(ObjectMapper objectMapper, MetricsService metricsService) {
        this(objectMapper, metricsService, BUCKET_CAPACITY, REFILL_DURATION);
    }

    public RateLimitInterceptor(
            ObjectMapper objectMapper,
            MetricsService metricsService,
            int capacity,
            Duration refillDuration) {
        this.objectMapper = objectMapper;
        this.metricsService = metricsService;
        this.capacity = capacity;
        this.refillDuration = refillDuration;
    }

    /** Stricter limit for unauthenticated endpoints that send email or check reset tokens. */
    public static RateLimitInterceptor forAuthEmail(
            ObjectMapper objectMapper, MetricsService metricsService) {
        return new RateLimitInterceptor(
                objectMapper, metricsService, AUTH_BUCKET_CAPACITY, AUTH_REFILL_DURATION);
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler)
            throws Exception {
        String ip = getClientIp(request);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> createBucket());

        if (bucket.tryConsume(TOKENS_PER_REQUEST)) {
            return true;
        }

        response.setStatus(TOO_MANY_REQUESTS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        ErrorResponse body = ErrorResponse.of(TOO_MANY_REQUESTS_MESSAGE, TOO_MANY_REQUESTS);
        response.getWriter().write(objectMapper.writeValueAsString(body));
        metricsService.recordRateLimitHit(request.getRequestURI());
        return false;
    }

    private Bucket createBucket() {
        Bandwidth limit =
                Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, refillDuration)
                        .build();
        return Bucket.builder().addLimit(limit).build();
    }

    private String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader(X_FORWARDED_FOR);
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
