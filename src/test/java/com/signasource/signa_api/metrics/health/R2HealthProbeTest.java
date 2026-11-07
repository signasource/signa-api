package com.signasource.signa_api.metrics.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.common.MetricsService;
import com.signasource.signa_api.config.R2Properties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.s3.S3Client;

@ExtendWith(MockitoExtension.class)
class R2HealthProbeTest {

    @Mock private S3Client r2Client;
    @Mock private MetricsService metricsService;

    private R2HealthProbe probe;

    @BeforeEach
    void setUp() {
        R2Properties props = new R2Properties("https://r2", "k", "s", "signa-animations", 15);
        probe = new R2HealthProbe(r2Client, props, metricsService, new SimpleMeterRegistry());
    }

    @Test
    @SuppressWarnings("unchecked")
    void marksUpWhenBucketReachable() {
        probe.probe();

        assertEquals(1, probe.isUp());
        verify(metricsService).recordR2Probe(any(Duration.class), eq(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void marksDownWhenProbeFails() {
        when(r2Client.headBucket(any(Consumer.class)))
                .thenThrow(new RuntimeException("unreachable"));

        probe.probe();

        assertEquals(0, probe.isUp());
        verify(metricsService).recordR2Probe(any(Duration.class), eq(false));
    }
}
