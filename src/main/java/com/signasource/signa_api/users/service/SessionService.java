package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.users.dto.RecordSessionRequest;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSession;
import com.signasource.signa_api.users.repository.UserSessionRepository;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Persists client-reported app-usage sessions, rejecting implausible durations/timestamps. */
@Service
@RequiredArgsConstructor
public class SessionService {

    /** Longest plausible single foreground session; anything above is treated as bad data. */
    static final int MAX_DURATION_SECONDS = (int) Duration.ofHours(6).toSeconds();

    /** Clock-skew tolerance for a client-supplied start instant. */
    private static final Duration FUTURE_SKEW = Duration.ofMinutes(5);

    private final UserSessionRepository repository;

    @Transactional
    public void record(User user, RecordSessionRequest request) {
        Instant now = Instant.now();
        if (request.durationSeconds() > MAX_DURATION_SECONDS) {
            throw new InvalidInputException(
                    "durationSeconds must not exceed " + MAX_DURATION_SECONDS);
        }
        if (request.startedAt().isAfter(now.plus(FUTURE_SKEW))) {
            throw new InvalidInputException("startedAt must not be in the future");
        }
        if (request.sessionId() != null) {
            var existing =
                    repository.findByUserIdAndClientSessionId(user.getId(), request.sessionId());
            if (existing.isPresent()) {
                UserSession session = existing.get();
                // A heartbeat only ever extends a session.
                session.setDurationSeconds(
                        Math.max(session.getDurationSeconds(), request.durationSeconds()));
                repository.save(session);
                return;
            }
        }
        repository.save(
                UserSession.builder()
                        .clientSessionId(request.sessionId())
                        .user(user)
                        .startedAt(request.startedAt())
                        .durationSeconds(request.durationSeconds())
                        .recordedAt(now)
                        .build());
    }
}
