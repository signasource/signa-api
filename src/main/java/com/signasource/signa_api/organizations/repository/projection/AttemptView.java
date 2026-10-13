package com.signasource.signa_api.organizations.repository.projection;

import java.time.Instant;
import java.util.UUID;

public interface AttemptView {
    UUID getUserId();

    Instant getAttemptedAt();

    /** {@code null} for INFO views, which count as activity but not as an exercise. */
    Boolean getIsCorrect();
}
