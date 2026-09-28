package com.signasource.signa_api.organizations.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface UserAttemptStatsView {

    UUID getUserId();

    Instant getLastActivityAt();

    long getEvaluated();

    long getCorrect();
}
