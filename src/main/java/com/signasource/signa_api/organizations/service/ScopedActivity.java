package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.AttemptView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.CourseScope;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.BiConsumer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The dashboard's activity queries, run course by course with only the people who take each course
 * (see {@link ContentScope#participantsOf}), so activity on a course someone doesn't take (e.g. one
 * whose seat was refunded) never counts, even if others in the organization do take it.
 */
@Component
@RequiredArgsConstructor
public class ScopedActivity {

    private final OrganizationMetricsRepository metricsRepository;

    /**
     * Per user: last attempt before {@code until} and evaluated/correct in {@code [from, until)}.
     */
    public List<UserAttemptStatsView> statsByUser(
            UUID organizationId,
            ContentScope scope,
            Collection<UUID> userIds,
            Instant from,
            Instant until) {
        Map<UUID, UserStats> merged = new LinkedHashMap<>();
        forEachCourse(
                scope,
                userIds,
                (courseId, users) -> {
                    for (UserAttemptStatsView row :
                            metricsRepository.findAttemptStatsByUser(
                                    organizationId, users, List.of(courseId), from, until)) {
                        merged.merge(
                                row.getUserId(),
                                new UserStats(
                                        row.getUserId(),
                                        row.getLastActivityAt(),
                                        row.getEvaluated(),
                                        row.getCorrect()),
                                UserStats::plus);
                    }
                });
        return List.copyOf(merged.values());
    }

    public List<AttemptView> attemptsBetween(
            UUID organizationId,
            ContentScope scope,
            Collection<UUID> userIds,
            Instant from,
            Instant to) {
        List<AttemptView> attempts = new ArrayList<>();
        forEachCourse(
                scope,
                userIds,
                (courseId, users) ->
                        attempts.addAll(
                                metricsRepository.findAttemptsBetween(
                                        organizationId, users, List.of(courseId), from, to)));
        return attempts;
    }

    /** A topic belongs to a single course, so rows never need merging. */
    public List<TopicAttemptStatsView> statsByTopic(
            UUID organizationId,
            ContentScope scope,
            Collection<UUID> userIds,
            Instant from,
            Instant until) {
        List<TopicAttemptStatsView> rows = new ArrayList<>();
        forEachCourse(
                scope,
                userIds,
                (courseId, users) ->
                        rows.addAll(
                                metricsRepository.findAttemptStatsByTopic(
                                        organizationId, users, List.of(courseId), from, until)));
        return rows;
    }

    public AttemptTotalsView totalsByBlockTypes(
            UUID organizationId,
            ContentScope scope,
            Collection<UUID> userIds,
            Collection<BlockType> types,
            Instant from,
            Instant until) {
        long[] totals = new long[2];
        forEachCourse(
                scope,
                userIds,
                (courseId, users) -> {
                    AttemptTotalsView row =
                            metricsRepository.findAttemptTotalsByBlockTypes(
                                    organizationId, users, List.of(courseId), types, from, until);
                    if (row != null) {
                        totals[0] += row.getEvaluated();
                        totals[1] += row.getCorrect();
                    }
                });
        return new Totals(totals[0], totals[1]);
    }

    public List<DailyAttemptStatsView> dailyStats(
            UUID organizationId,
            ContentScope scope,
            Collection<UUID> userIds,
            Instant since,
            Instant until) {
        Map<LocalDate, DailyStats> merged = new TreeMap<>();
        forEachCourse(
                scope,
                userIds,
                (courseId, users) -> {
                    for (DailyAttemptStatsView row :
                            metricsRepository.findDailyAttemptStats(
                                    organizationId, users, List.of(courseId), since, until)) {
                        merged.merge(
                                row.getDay(),
                                new DailyStats(row.getDay(), row.getEvaluated(), row.getCorrect()),
                                DailyStats::plus);
                    }
                });
        return List.copyOf(merged.values());
    }

    /** Lessons completed in {@code [from, to)}, each on a version of a course its learner takes. */
    public List<Instant> lessonCompletionsBetween(
            ContentScope scope, Collection<UUID> userIds, Instant from, Instant to) {
        List<Instant> completions = new ArrayList<>();
        forEachCourse(
                scope,
                userIds,
                (courseId, users) ->
                        completions.addAll(
                                metricsRepository.findLessonCompletionsBetween(
                                        users, scope.versionIdsOfCourse(courseId), from, to)));
        return completions;
    }

    public long topicsCompletedBetween(
            ContentScope scope, Collection<UUID> userIds, Instant from, Instant until) {
        long[] total = new long[1];
        forEachCourse(
                scope,
                userIds,
                (courseId, users) ->
                        total[0] +=
                                metricsRepository.countTopicsCompletedBetween(
                                        users, scope.versionIdsOfCourse(courseId), from, until));
        return total[0];
    }

    private static void forEachCourse(
            ContentScope scope, Collection<UUID> userIds, BiConsumer<UUID, List<UUID>> action) {
        for (CourseScope course : scope.courses()) {
            UUID courseId = course.course().getId();
            List<UUID> users = scope.participantsOf(courseId, userIds);
            if (!users.isEmpty()) {
                action.accept(courseId, users);
            }
        }
    }

    private record UserStats(UUID userId, Instant lastActivityAt, long evaluated, long correct)
            implements UserAttemptStatsView {
        UserStats plus(UserStats other) {
            Instant last =
                    lastActivityAt == null
                                    || (other.lastActivityAt != null
                                            && other.lastActivityAt.isAfter(lastActivityAt))
                            ? other.lastActivityAt
                            : lastActivityAt;
            return new UserStats(
                    userId, last, evaluated + other.evaluated, correct + other.correct);
        }

        @Override
        public UUID getUserId() {
            return userId;
        }

        @Override
        public Instant getLastActivityAt() {
            return lastActivityAt;
        }

        @Override
        public long getEvaluated() {
            return evaluated;
        }

        @Override
        public long getCorrect() {
            return correct;
        }
    }

    private record DailyStats(LocalDate day, long evaluated, long correct)
            implements DailyAttemptStatsView {
        DailyStats plus(DailyStats other) {
            return new DailyStats(day, evaluated + other.evaluated, correct + other.correct);
        }

        @Override
        public LocalDate getDay() {
            return day;
        }

        @Override
        public long getEvaluated() {
            return evaluated;
        }

        @Override
        public long getCorrect() {
            return correct;
        }
    }

    private record Totals(long evaluated, long correct) implements AttemptTotalsView {
        @Override
        public long getEvaluated() {
            return evaluated;
        }

        @Override
        public long getCorrect() {
            return correct;
        }
    }
}
