package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse.ActiveParticipant;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse.FollowUpParticipant;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse.FollowUpReason;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.projection.AttemptView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.dto.AvatarLinks;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The panel's home: how far participants got, who was active lately and who needs a nudge.
 *
 * <p>Who takes which course is {@link ParticipantAccessResolver}'s rule (a paid seat; a refunded
 * one leaves the course's figures, activity included). Figures are current: there is no period.
 */
@Service
@RequiredArgsConstructor
public class OrganizationHomeService {

    static final int ACTIVE_WINDOW_DAYS = 7;
    static final int INACTIVE_AFTER_DAYS = 7;
    static final int EXPIRING_WITHIN_DAYS = 7;

    /** Percentage points behind an even pace that count as low progress. */
    static final int BEHIND_PACE_POINTS = 25;

    private final OrganizationAccessService accessService;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationContentScope contentScope;
    private final ScopedActivity scopedActivity;
    private final PurchasePolicy policy;

    @Transactional(readOnly = true)
    public OrganizationHomeResponse getHome(User actor, UUID organizationId, UUID courseId) {
        accessService.requireManage(actor, organizationId);
        return home(organizationId, courseId, Instant.now());
    }

    OrganizationHomeResponse home(UUID organizationId, UUID courseId, Instant now) {
        Map<UUID, OrganizationMember> members =
                memberRepository
                        .findByOrganizationIdAndRoleAndStatus(
                                organizationId, MemberRole.MEMBER, MemberStatus.ACTIVE)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        m -> m.getUser().getId(),
                                        m -> m,
                                        (a, b) -> a,
                                        LinkedHashMap::new));
        ContentScope scope = contentScope.resolve(organizationId, courseId, members.keySet(), now);
        List<UUID> participantIds = scope.participants(members.keySet());
        if (participantIds.isEmpty()) {
            return OrganizationHomeResponse.empty(ACTIVE_WINDOW_DAYS);
        }
        Map<UUID, Activity> activity =
                activity(
                        organizationId,
                        scope,
                        participantIds,
                        now.minus(days(ACTIVE_WINDOW_DAYS)),
                        now);

        long started = 0;
        long startedProgressSum = 0;
        long completed = 0;
        List<ActiveParticipant> active = new ArrayList<>();
        List<Ranked> followUp = new ArrayList<>();
        for (UUID userId : participantIds) {
            User user = members.get(userId).getUser();
            Activity done = activity.getOrDefault(userId, Activity.NONE);
            List<CourseProgress> courses =
                    progress(scope, userId, scope.accessesOf(userId).values());
            long lessons = courses.stream().mapToLong(CourseProgress::lessons).sum();
            long completedLessons = courses.stream().mapToLong(CourseProgress::completed).sum();
            int progress = MetricMath.percentage(completedLessons, lessons);

            if (completedLessons > 0 || done.lastActivityAt() != null) {
                started++;
                startedProgressSum += progress;
            }
            if (lessons > 0 && completedLessons >= lessons) {
                completed++;
            }
            if (done.activities() > 0) {
                active.add(
                        new ActiveParticipant(
                                userId,
                                user.getName(),
                                user.getLastName(),
                                AvatarLinks.of(user),
                                done.activities(),
                                done.days().size(),
                                done.lastActivityAt(),
                                progress));
            }
            Ranked ranked = followUp(members.get(userId), courses, done, now);
            if (ranked != null) {
                followUp.add(ranked);
            }
        }

        long participants = participantIds.size();
        active.sort(
                Comparator.comparingLong(ActiveParticipant::activities)
                        .thenComparingInt(ActiveParticipant::activeDays)
                        .thenComparing(ActiveParticipant::lastActivityAt)
                        .reversed());
        followUp.sort(Ranked.ORDER);
        return new OrganizationHomeResponse(
                participants,
                started,
                started == 0 ? 0 : (int) Math.round(startedProgressSum / (double) started),
                completed,
                MetricMath.percentage(completed, participants),
                ACTIVE_WINDOW_DAYS,
                List.copyOf(active),
                followUp.stream().map(Ranked::participant).toList());
    }

    /**
     * Last activity ever and the window's attempts, counted only on the courses each person takes
     * (see {@link ScopedActivity}).
     */
    private Map<UUID, Activity> activity(
            UUID organizationId,
            ContentScope scope,
            List<UUID> userIds,
            Instant windowStart,
            Instant now) {
        Map<UUID, Activity> activity = new HashMap<>();
        for (UserAttemptStatsView stats :
                scopedActivity.statsByUser(organizationId, scope, userIds, now, now)) {
            activity.put(stats.getUserId(), new Activity(0, Set.of(), stats.getLastActivityAt()));
        }
        scopedActivity.attemptsBetween(organizationId, scope, userIds, windowStart, now).stream()
                .collect(Collectors.groupingBy(AttemptView::getUserId))
                .forEach(
                        (userId, list) ->
                                activity.merge(
                                        userId,
                                        new Activity(
                                                list.size(),
                                                list.stream()
                                                        .map(
                                                                a ->
                                                                        ArgentinaTime.dayOf(
                                                                                a.getAttemptedAt()))
                                                        .collect(Collectors.toSet()),
                                                null),
                                        Activity::merge));
        return activity;
    }

    private static List<CourseProgress> progress(
            ContentScope scope, UUID userId, Iterable<CourseAccess> accesses) {
        List<CourseProgress> courses = new ArrayList<>();
        for (CourseAccess access : accesses) {
            CourseVersion version = scope.versionIn(userId, access.courseId());
            if (version == null) {
                continue;
            }
            courses.add(
                    new CourseProgress(
                            access,
                            scope.lessonsIn(version.getId()),
                            scope.completedIn(userId, version.getId())));
        }
        return courses;
    }

    /**
     * Who needs a nudge and why, judged on the courses still running that they have not finished.
     * Nobody new to the course is called inactive before {@link #INACTIVE_AFTER_DAYS}.
     */
    private Ranked followUp(
            OrganizationMember member, List<CourseProgress> courses, Activity done, Instant now) {
        List<CourseProgress> running =
                courses.stream().filter(c -> c.access().isRunning(now) && !c.isFinished()).toList();
        if (running.isEmpty()) {
            return null;
        }

        Instant expiresAt =
                running.stream()
                        .map(c -> c.access().expiresAt())
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        Instant inactiveSince = now.minus(days(INACTIVE_AFTER_DAYS));
        Instant startedAt =
                running.stream()
                        .map(c -> c.access().startedAt())
                        .filter(Objects::nonNull)
                        .min(Comparator.naturalOrder())
                        .orElse(null);
        boolean inactive =
                (done.lastActivityAt() == null || done.lastActivityAt().isBefore(inactiveSince))
                        && (startedAt == null || startedAt.isBefore(inactiveSince));

        List<CourseProgress> paced = running.stream().filter(c -> c.access().hasPace()).toList();
        Integer expected = expectedPercentage(paced, now);
        boolean behind = expected != null && percentage(paced) + BEHIND_PACE_POINTS <= expected;

        List<FollowUpReason> reasons = new ArrayList<>();
        if (expiresAt != null && expiresAt.isBefore(now.plus(days(EXPIRING_WITHIN_DAYS)))) {
            reasons.add(FollowUpReason.ACCESS_EXPIRING);
        }
        if (inactive) {
            reasons.add(FollowUpReason.INACTIVE);
        }
        if (behind) {
            reasons.add(FollowUpReason.LOW_PROGRESS);
        }
        if (reasons.isEmpty()) {
            return null;
        }

        User user = member.getUser();
        Instant lastReminderAt = member.getLastReminderAt();
        Instant nextReminderAt =
                lastReminderAt == null
                        ? null
                        : lastReminderAt.plus(policy.manualReminderCooldown());
        return new Ranked(
                new FollowUpParticipant(
                        user.getId(),
                        user.getName(),
                        user.getLastName(),
                        AvatarLinks.of(user),
                        List.copyOf(reasons),
                        percentage(running),
                        expected,
                        done.lastActivityAt(),
                        expiresAt,
                        lastReminderAt,
                        nextReminderAt != null && nextReminderAt.isAfter(now)
                                ? nextReminderAt
                                : null),
                reasons.stream().mapToInt(OrganizationHomeService::weight).sum());
    }

    private static int percentage(List<CourseProgress> courses) {
        return MetricMath.percentage(
                courses.stream().mapToLong(CourseProgress::completed).sum(),
                courses.stream().mapToLong(CourseProgress::lessons).sum());
    }

    /**
     * Where an even pace through each access would have them by now, weighted by lessons; {@code
     * null} when no course has a pace.
     */
    private static Integer expectedPercentage(List<CourseProgress> paced, Instant now) {
        long lessons = paced.stream().mapToLong(CourseProgress::lessons).sum();
        if (lessons == 0) {
            return null;
        }
        double expected =
                paced.stream().mapToDouble(c -> c.lessons() * c.access().elapsed(now)).sum();
        return (int) Math.round(expected * 100 / lessons);
    }

    /** An expiring access outranks inactivity, which outranks a slow pace (and any two, one). */
    private static int weight(FollowUpReason reason) {
        return switch (reason) {
            case ACCESS_EXPIRING -> 4;
            case INACTIVE -> 2;
            case LOW_PROGRESS -> 1;
        };
    }

    private static Duration days(int days) {
        return Duration.ofDays(days);
    }

    private record CourseProgress(CourseAccess access, long lessons, long completed) {
        boolean isFinished() {
            return lessons > 0 && completed >= lessons;
        }
    }

    /** Attempts in the window, the days they fell on, and the last attempt ever. */
    private record Activity(long activities, Set<LocalDate> days, Instant lastActivityAt) {
        static final Activity NONE = new Activity(0, Set.of(), null);

        Activity merge(Activity other) {
            Set<LocalDate> allDays = new HashSet<>(days);
            allDays.addAll(other.days);
            Instant last =
                    lastActivityAt == null || other.lastActivityAt == null
                            ? Objects.requireNonNullElse(lastActivityAt, other.lastActivityAt)
                            : (lastActivityAt.isAfter(other.lastActivityAt)
                                    ? lastActivityAt
                                    : other.lastActivityAt);
            return new Activity(activities + other.activities, Set.copyOf(allDays), last);
        }
    }

    private record Ranked(FollowUpParticipant participant, int weight) {
        static final Comparator<Ranked> ORDER =
                Comparator.comparingInt(Ranked::weight)
                        .reversed()
                        .thenComparing(
                                r -> r.participant().accessExpiresAt(),
                                Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(
                                r -> r.participant().lastActivityAt(),
                                Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(
                                r -> r.participant().name(),
                                Comparator.nullsLast(Comparator.naturalOrder()));
    }
}
