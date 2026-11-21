package com.signasource.signa_api.organizations.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * The panel's home, for every contracted course or one of them.
 *
 * @param participants people whose access to a course in scope comes from a paid purchase
 * @param averageProgressPercentage among {@code startedParticipants} only
 * @param completedPercentage {@code completedParticipants / participants}
 * @param activeParticipants who had activity in the last {@code activeWindowDays}, most active
 *     first
 * @param followUp who needs a nudge, highest priority first
 */
public record OrganizationHomeResponse(
        long participants,
        long startedParticipants,
        int averageProgressPercentage,
        long completedParticipants,
        int completedPercentage,
        int activeWindowDays,
        List<ActiveParticipant> activeParticipants,
        List<FollowUpParticipant> followUp) {

    public static OrganizationHomeResponse empty(int activeWindowDays) {
        return new OrganizationHomeResponse(0, 0, 0, 0, 0, activeWindowDays, List.of(), List.of());
    }

    /**
     * @param activities lesson blocks answered or viewed in the window
     * @param activeDays distinct days (Argentine time) with activity in the window
     */
    public record ActiveParticipant(
            UUID userId,
            String name,
            String lastName,
            String avatarUrl,
            long activities,
            int activeDays,
            Instant lastActivityAt,
            int progressPercentage) {}

    /**
     * Someone whose access is still running on a course they have not finished.
     *
     * @param progressPercentage over the courses still running and unfinished
     * @param expectedProgressPercentage where an even pace would have them by now; {@code null}
     *     when no running access has an end date
     * @param accessExpiresAt the soonest end among those courses; {@code null} when none ends
     * @param nextReminderAvailableAt when the admin can send another reminder; {@code null} when
     *     they can now or never sent one
     */
    public record FollowUpParticipant(
            UUID userId,
            String name,
            String lastName,
            String avatarUrl,
            List<FollowUpReason> reasons,
            int progressPercentage,
            Integer expectedProgressPercentage,
            Instant lastActivityAt,
            Instant accessExpiresAt,
            Instant lastReminderAt,
            Instant nextReminderAvailableAt) {}

    /** In priority order. */
    public enum FollowUpReason {
        ACCESS_EXPIRING,
        INACTIVE,
        LOW_PROGRESS
    }
}
