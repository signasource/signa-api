package com.signasource.signa_api.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class RateLimitInterceptor implements HandlerInterceptor {

    static final int BUCKET_CAPACITY = 30;
    static final Duration REFILL_DURATION = Duration.ofMinutes(1);
    static final int TOO_MANY_REQUESTS = 429;
    private static final int TOKENS_PER_REQUEST = 1;
    private static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

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
        response.getWriter().write("{\"message\": \"Too many requests\", \"status\": 429}");
        return false;
    }

    private Bucket createBucket() {
        Bandwidth limit =
                Bandwidth.builder()
                        .capacity(BUCKET_CAPACITY)
                        .refillGreedy(BUCKET_CAPACITY, REFILL_DURATION)
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
