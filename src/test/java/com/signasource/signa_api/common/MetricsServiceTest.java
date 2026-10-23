package com.signasource.signa_api.common;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.signasource.signa_api.learning.entity.BlockType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MetricsServiceTest {

    private MeterRegistry registry;
    private MetricsService metricsService;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metricsService = new MetricsService(registry);
    }

    @Test
    void shouldRecordUserRegistration() {
        metricsService.recordUserRegistration("local");
        assertEquals(
                1.0, registry.counter("signa.user.registrations", "provider", "local").count());
    }

    @Test
    void shouldRecordUserLogin() {
        metricsService.recordUserLogin("google");
        assertEquals(1.0, registry.counter("signa.user.logins", "provider", "google").count());
    }

    @Test
    void shouldRecordCourseEnrollment() {
        metricsService.recordCourseEnrollment();
        assertEquals(1.0, registry.counter("signa.course.enrollments").count());
    }

    @Test
    void shouldRecordBlockAttemptCorrect() {
        metricsService.recordBlockAttempt(BlockType.SELECT_MEANING, true);
        assertEquals(
                1.0,
                registry.counter(
                                "signa.lesson.block.attempts",
                                "type",
                                "SELECT_MEANING",
                                "result",
                                "correct")
                        .count());
    }

    @Test
    void shouldRecordBlockAttemptIncorrect() {
        metricsService.recordBlockAttempt(BlockType.SELECT_MEANING, false);
        assertEquals(
                1.0,
                registry.counter(
                                "signa.lesson.block.attempts",
                                "type",
                                "SELECT_MEANING",
                                "result",
                                "incorrect")
                        .count());
    }

    @Test
    void shouldRecordBlockAttemptViewedForInfoBlock() {
        metricsService.recordBlockAttempt(BlockType.INFO, null);
        assertEquals(
                1.0,
                registry.counter("signa.lesson.block.attempts", "type", "INFO", "result", "viewed")
                        .count());
    }

    @Test
    void shouldRecordLessonCompletion() {
        metricsService.recordLessonCompletion();
        assertEquals(1.0, registry.counter("signa.lesson.completions").count());
    }

    @Test
    void shouldRecordTopicCompletion() {
        metricsService.recordTopicCompletion();
        assertEquals(1.0, registry.counter("signa.topic.completions").count());
    }

    @Test
    void shouldRecordCourseCompletion() {
        metricsService.recordCourseCompletion();
        assertEquals(1.0, registry.counter("signa.course.completions").count());
    }

    @Test
    void shouldRecordXpEarned() {
        metricsService.recordXpEarned(50);
        assertEquals(50.0, registry.counter("signa.xp.earned").count());
    }

    @Test
    void shouldRecordSignsLearned() {
        metricsService.recordSignsLearned(3);
        assertEquals(3.0, registry.counter("signa.signs.learned").count());
    }

    @Test
    void shouldRecordLifeLost() {
        metricsService.recordLifeLost();
        assertEquals(1.0, registry.counter("signa.lives.lost").count());
    }

    @Test
    void shouldRecordWaitlistSignup() {
        metricsService.recordWaitlistSignup();
        assertEquals(1.0, registry.counter("signa.waitlist.signups").count());
    }

    @Test
    void shouldRecordRateLimitHit() {
        metricsService.recordRateLimitHit("/users/username-availability");
        assertEquals(
                1.0,
                registry.counter(
                                "signa.rate.limit.hits", "endpoint", "/users/username-availability")
                        .count());
    }
}
