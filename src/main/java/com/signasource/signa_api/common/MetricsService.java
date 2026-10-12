package com.signasource.signa_api.common;

import com.signasource.signa_api.learning.entity.BlockType;
import io.micrometer.core.instrument.MeterRegistry;
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
}
