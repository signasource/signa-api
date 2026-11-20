package com.signasource.signa_api.metrics.health;

import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.config.R2Properties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Periodically checks that the R2 (animations) bucket is reachable and publishes the result as the
 * {@code signa.r2.up} gauge (1 up / 0 down) plus a probe-latency timer. Deliberately NOT a Spring
 * {@code HealthIndicator}: the dashboard tells R2 apart from the backend by reading this gauge, and
 * a transient R2 blip must not flip the aggregate {@code /actuator/health} used for availability.
 */
@Component
@Slf4j
public class R2HealthProbe {

    private final S3Client r2Client;
    private final R2Properties r2Properties;
    private final MetricsService metricsService;
    private final AtomicInteger up = new AtomicInteger(0);

    public R2HealthProbe(
            S3Client r2Client,
            R2Properties r2Properties,
            MetricsService metricsService,
            MeterRegistry registry) {
        this.r2Client = r2Client;
        this.r2Properties = r2Properties;
        this.metricsService = metricsService;
        Gauge.builder("signa.r2.up", up, AtomicInteger::get)
                .description("Whether the R2 animations bucket answered the last probe")
                .register(registry);
    }

    @Scheduled(fixedRate = 60_000L)
    public void probe() {
        long start = System.nanoTime();
        boolean ok = true;
        try {
            r2Client.headBucket(b -> b.bucket(r2Properties.bucket()));
        } catch (RuntimeException ex) {
            ok = false;
            log.warn("R2 reachability probe failed: {}", ex.getMessage());
        }
        up.set(ok ? 1 : 0);
        metricsService.recordR2Probe(Duration.ofNanos(System.nanoTime() - start), ok);
    }

    /** Last known reachability (1 up / 0 down); exposed for tests and callers. */
    public int isUp() {
        return up.get();
    }
}
