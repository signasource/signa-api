package com.signasource.signa_api.users.service;

import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserActivity;
import com.signasource.signa_api.users.repository.UserActivityRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Records that a learner did something in the app. Called for every authenticated request but
 * throttled to one row per user per {@link #THROTTLE}, so write volume stays small. Never throws:
 * telemetry must not break the request.
 */
@Slf4j
@Service
public class UserActivityRecorder {

    static final Duration THROTTLE = Duration.ofMinutes(1);

    private final UserActivityRepository repository;
    private final Clock clock;
    private final Map<UUID, Instant> lastRecorded = new ConcurrentHashMap<>();

    @Autowired
    public UserActivityRecorder(UserActivityRepository repository) {
        this(repository, Clock.systemUTC());
    }

    UserActivityRecorder(UserActivityRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public void touch(User user) {
        if (user == null || user.getId() == null || user.getRole() == Role.ADMIN) {
            return;
        }
        Instant now = clock.instant();
        Instant last = lastRecorded.get(user.getId());
        if (last != null && Duration.between(last, now).compareTo(THROTTLE) < 0) {
            return;
        }
        lastRecorded.put(user.getId(), now);
        try {
            repository.save(UserActivity.builder().user(user).occurredAt(now).build());
        } catch (RuntimeException e) {
            log.warn("Could not record user activity", e);
        }
    }
}
