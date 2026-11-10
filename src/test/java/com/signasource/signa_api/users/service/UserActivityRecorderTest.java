package com.signasource.signa_api.users.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserActivity;
import com.signasource.signa_api.users.repository.UserActivityRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class UserActivityRecorderTest {

    private final UserActivityRepository repository = Mockito.mock(UserActivityRepository.class);
    private final Instant t0 = Instant.parse("2026-11-10T10:00:00Z");

    private User learner() {
        return User.builder().id(UUID.randomUUID()).role(Role.USER).build();
    }

    @Test
    void manyRequestsInsideTheThrottleWindowAreOneRow() {
        var recorder = new UserActivityRecorder(repository, Clock.fixed(t0, ZoneOffset.UTC));
        User user = learner();
        for (int i = 0; i < 20; i++) recorder.touch(user);
        verify(repository, times(1)).save(any(UserActivity.class));
    }

    @Test
    void recordsAgainAfterTheWindow() {
        User user = learner();
        new UserActivityRecorder(repository, Clock.fixed(t0, ZoneOffset.UTC)).touch(user);
        var later = Clock.fixed(t0.plus(Duration.ofMinutes(5)), ZoneOffset.UTC);
        var recorder = new UserActivityRecorder(repository, later);
        recorder.touch(user);
        recorder.touch(user);
        verify(repository, times(2)).save(any(UserActivity.class));
    }

    @Test
    void ignoresAdminsAndSwallowsFailures() {
        var recorder = new UserActivityRecorder(repository, Clock.fixed(t0, ZoneOffset.UTC));
        recorder.touch(User.builder().id(UUID.randomUUID()).role(Role.ADMIN).build());
        verify(repository, never()).save(any());

        Mockito.when(repository.save(any())).thenThrow(new RuntimeException("db down"));
        recorder.touch(learner());
    }
}
