package com.signasource.signa_api.organizations.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant's access to one course of the organization.
 *
 * @param startedAt when it started (seat activation, or enrollment for older ones)
 * @param expiresAt when it ends; {@code null} when it doesn't
 * @param revoked the person was removed from the organization (or the enrollment dropped)
 */
public record CourseAccess(UUID courseId, Instant startedAt, Instant expiresAt, boolean revoked) {

    /** An access with no dates, e.g. for tests or courses with no end. */
    public static CourseAccess open(UUID courseId) {
        return new CourseAccess(courseId, null, null, false);
    }

    public boolean isRunning(Instant now) {
        return !revoked && (expiresAt == null || expiresAt.isAfter(now));
    }

    public boolean hasPace() {
        return startedAt != null && expiresAt != null && expiresAt.isAfter(startedAt);
    }

    /** Share of the access already gone, 0 to 1; only meaningful when {@link #hasPace()}. */
    public double elapsed(Instant now) {
        double total = Duration.between(startedAt, expiresAt).toMillis();
        double gone = Duration.between(startedAt, now).toMillis();
        return Math.max(0, Math.min(1, gone / total));
    }

    /** Two grants of one course: the widest access, revoked only if both are. */
    CourseAccess merge(CourseAccess other) {
        Instant start =
                startedAt == null || other.startedAt == null
                        ? Objects.requireNonNullElse(startedAt, other.startedAt)
                        : (startedAt.isBefore(other.startedAt) ? startedAt : other.startedAt);
        Instant end =
                expiresAt == null || other.expiresAt == null
                        ? null
                        : (expiresAt.isAfter(other.expiresAt) ? expiresAt : other.expiresAt);
        return new CourseAccess(courseId, start, end, revoked && other.revoked);
    }
}
