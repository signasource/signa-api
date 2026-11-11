package com.signasource.signa_api.common;

import com.signasource.signa_api.learning.entity.BlockType;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MetricsService {

    private final MeterRegistry registry;

    public void recordUserRegistration(String provider) {
        registry.counter("signa.user.registrations", "provider", provider).increment();
    }

    public void recordUserLogin(String provider) {
        registry.counter("signa.user.logins", "provider", provider).increment();
    }

    public void recordCourseEnrollment() {
        registry.counter("signa.course.enrollments").increment();
    }

    public void recordBlockAttempt(BlockType blockType, Boolean isCorrect) {
        String result = isCorrect == null ? "viewed" : (isCorrect ? "correct" : "incorrect");
        registry.counter("signa.lesson.block.attempts", "type", blockType.name(), "result", result)
                .increment();
    }

    public void recordLessonCompletion() {
        registry.counter("signa.lesson.completions").increment();
    }

    public void recordTopicCompletion() {
        registry.counter("signa.topic.completions").increment();
    }

    public void recordCourseCompletion() {
        registry.counter("signa.course.completions").increment();
    }

    public void recordXpEarned(int amount) {
        registry.counter("signa.xp.earned").increment(amount);
    }

    public void recordSignsLearned(int count) {
        registry.counter("signa.signs.learned").increment(count);
    }

    public void recordLifeLost() {
        registry.counter("signa.lives.lost").increment();
    }

    public void recordWaitlistSignup() {
        registry.counter("signa.waitlist.signups").increment();
    }

    public void recordRateLimitHit(String endpoint) {
        registry.counter("signa.rate.limit.hits", "endpoint", endpoint).increment();
    }

    // --- Performance / reliability -------------------------------------------------------------

    /**
     * Time taken to presign an R2 (animations) download URL, split by outcome. A percentile
     * histogram so Grafana can read p95/p99 latency of the animation critical path.
     */
    public void recordR2Presign(Duration duration, boolean success) {
        timer("signa.r2.presign.duration", "result", success ? "success" : "failure")
                .record(duration);
    }

    /** Result of the periodic R2 reachability probe (see R2HealthIndicator). */
    public void recordR2Probe(Duration duration, boolean up) {
        timer("signa.r2.probe.duration", "result", up ? "up" : "down").record(duration);
    }

    /**
     * An HTTP error served by the API, tagged by exception type and status, for error-rate panels.
     */
    public void recordHttpError(String type, int status) {
        registry.counter("signa.http.errors", "type", type, "status", Integer.toString(status))
                .increment();
    }

    /** A transactional email that was handed off to the SMTP server successfully. */
    public void recordEmailSent() {
        registry.counter("signa.email.sent").increment();
    }

    /** A transactional email that failed to send (SMTP down, bad credentials, …). */
    public void recordEmailFailure() {
        registry.counter("signa.email.failures").increment();
    }

    /** Push notifications that FCM accepted for delivery (per-token success count). */
    public void recordPushSent(int count) {
        if (count > 0) {
            registry.counter("signa.push.sent").increment(count);
        }
    }

    /** A push notification batch that FCM rejected or that failed to send. */
    public void recordPushFailure() {
        registry.counter("signa.push.failures").increment();
    }

    // --- Client telemetry (web / mobile) -------------------------------------------------------
    // Callers must pass values from a bounded allow-list (see TelemetryService); never user data.

    public void recordClientWebVital(String platform, String name, Duration duration) {
        timer("signa.client.web_vital", "platform", platform, "name", name).record(duration);
    }

    public void recordClientScreenLoad(String platform, String name, Duration duration) {
        timer("signa.client.screen_load", "platform", platform, "name", name).record(duration);
    }

    public void recordClientAnimationLoad(String platform, boolean success, Duration duration) {
        timer(
                        "signa.client.animation_load",
                        "platform",
                        platform,
                        "result",
                        success ? "success" : "failure")
                .record(duration);
    }

    public void recordClientError(String platform, String type) {
        registry.counter("signa.client.errors", "platform", platform, "type", type).increment();
    }

    /**
     * A camera exercise (PERFORM_SIGN / SPELL_NAME) the user skipped instead of attempting. Counted
     * by platform only — never by sign, to keep Prometheus cardinality bounded.
     */
    public void recordClientCameraSkip(String platform) {
        registry.counter("signa.client.camera_skip", "platform", platform).increment();
    }

    /** On-device sign-recognition inference latency per frame, reported by the mobile app. */
    public void recordClientInference(String platform, Duration duration) {
        timer("signa.client.inference", "platform", platform).record(duration);
    }

    /** Model confidence of a recognition sample (0–100), reported by the mobile app. */
    public void recordClientConfidence(String platform, double confidencePercent) {
        summary("signa.client.recognition_confidence", platform).record(confidencePercent);
    }

    /** Camera frame rate during live recognition, reported by the mobile app. */
    public void recordClientFps(String platform, double fps) {
        summary("signa.client.recognition_fps", platform).record(fps);
    }

    /**
     * A percentile-histogram timer, created once and cached by Micrometer per name+tags. The
     * histogram buckets are what let Grafana compute quantiles with {@code histogram_quantile}.
     */
    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).publishPercentileHistogram().register(registry);
    }

    /**
     * A percentile-histogram distribution summary for non-duration client signals (confidence,
     * fps).
     */
    private DistributionSummary summary(String name, String platform) {
        return DistributionSummary.builder(name)
                .tags("platform", platform)
                .publishPercentileHistogram()
                .register(registry);
    }
}
