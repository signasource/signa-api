package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse.ActivityFigures;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse.DailyActivity;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse.Participation;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse.Performance;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse.Progress;
import com.signasource.signa_api.organizations.dto.WeeklyPerformanceResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.AttemptView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.DailyMinutesView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.repository.projection.UserTopicStateView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.CourseScope;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Organization-wide participation, progress and performance indicators, measured over a {@link
 * MetricsPeriod} (see there for which figures count inside the period and which as of its end).
 */
@Service
@RequiredArgsConstructor
public class OrganizationMetricsService {

    static final int ACTIVE_WINDOW_DAYS = 7;
    private static final int EVOLUTION_WEEKS = 8;
    private static final List<BlockType> SIGN_RECOGNITION_TYPES = List.of(BlockType.PERFORM_SIGN);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationMetricsRepository metricsRepository;
    private final TopicRepository topicRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationContentScope contentScope;
    private final ScopedActivity scopedActivity;

    @Transactional(readOnly = true)
    public OrganizationOverviewResponse getOverview(
            User actor, UUID organizationId, UUID courseId, MetricsPeriod period) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);

        Instant start = period.start();
        Instant end = period.end();
        List<UUID> members = participantIds(organizationId, end);
        ContentScope scope = contentScope.resolve(organizationId, courseId, members, end);
        List<UUID> userIds = scope.participants(members);
        if (userIds.isEmpty()) {
            return new OrganizationOverviewResponse(
                    new Participation(0, 0, 0, 0, period.activeWindowDays(), 0, 0, 0),
                    new Progress(0, 0, 0, 0, 0, 0, 0, 0),
                    new Performance(0, 0, 0, 0, false, evolution(List.of(), period)));
        }

        long participants = userIds.size();

        List<UserAttemptStatsView> attemptStats =
                scopedActivity.statsByUser(organizationId, scope, userIds, start, end);
        Instant activeSince = period.activeSince();
        long active =
                attemptStats.stream()
                        .filter(s -> !s.getLastActivityAt().isBefore(activeSince))
                        .count();
        long exerciseAttempts =
                attemptStats.stream().mapToLong(UserAttemptStatsView::getEvaluated).sum();
        long correct = attemptStats.stream().mapToLong(UserAttemptStatsView::getCorrect).sum();

        List<Integer> streaks = metricsRepository.findCurrentStreaks(userIds);
        long activeDays =
                metricsRepository
                        .findActiveDaysByUser(userIds, period.activeDaysSince(), period.lastDay())
                        .stream()
                        .mapToLong(UserCountView::getTotal)
                        .sum();
        long minutes =
                metricsRepository
                        .findLearningMinutesByUserScopedToOrg(
                                userIds, organizationId, period.firstDay(), period.lastDay())
                        .stream()
                        .mapToLong(UserCountView::getTotal)
                        .sum();

        Participation participation =
                new Participation(
                        participants,
                        active,
                        participants - active,
                        attemptStats.size(),
                        period.activeWindowDays(),
                        MetricMath.average(activeDays, participants),
                        streaks.stream().filter(s -> s > 0).count(),
                        streaks.stream().mapToInt(Integer::intValue).max().orElse(0));

        // Each participant against their own version of each course (see OrganizationContentScope).
        long percentageSum = 0;
        long finishedEverything = 0;
        long pendingLessons = 0;
        for (UUID userId : userIds) {
            long total = scope.totalLessons(userId);
            long done = scope.completedLessons(userId);
            percentageSum += MetricMath.percentage(done, total);
            if (total > 0 && done >= total) {
                finishedEverything++;
            }
            pendingLessons += Math.max(0, total - done);
        }
        long completedInPeriod =
                scopedActivity.lessonCompletionsBetween(scope, userIds, start, end).size();
        long modulesCompleted = scopedActivity.topicsCompletedBetween(scope, userIds, start, end);
        Progress progress =
                new Progress(
                        (int) Math.round(percentageSum / (double) participants),
                        completedInPeriod,
                        pendingLessons,
                        modulesCompleted,
                        finishedEverything,
                        minutes,
                        MetricMath.average(minutes, participants),
                        (int) Math.round(pendingLessons / (double) participants));

        AttemptTotalsView recognition =
                scopedActivity.totalsByBlockTypes(
                        organizationId, scope, userIds, SIGN_RECOGNITION_TYPES, start, end);
        boolean hasCameraExercises =
                !scope.versionIds().isEmpty()
                        && metricsRepository.countCameraExercises(scope.versionIds()) > 0;
        Performance performance =
                new Performance(
                        exerciseAttempts,
                        MetricMath.percentage(correct, exerciseAttempts),
                        recognition.getEvaluated(),
                        MetricMath.percentage(recognition.getCorrect(), recognition.getEvaluated()),
                        hasCameraExercises,
                        evolution(
                                scopedActivity.dailyStats(
                                        organizationId,
                                        scope,
                                        userIds,
                                        evolutionStart(period),
                                        end),
                                period));

        return new OrganizationOverviewResponse(participation, progress, performance);
    }

    /**
     * Per module: who had completed it and who was on it as of the end of the period, and the
     * accuracy of the attempts made during the period.
     *
     * <p>Rows are the modules of each course's reference version. Participants on another version
     * of the course count in the row with the same position (versions are forked module by module),
     * and each participant only counts on their own version; a module that exists only in an older
     * version gets its own row.
     */
    @Transactional(readOnly = true)
    public List<ModuleStatsResponse> getModuleStats(
            User actor, UUID organizationId, UUID courseId, MetricsPeriod period) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);

        Instant end = period.end();
        List<UUID> members = participantIds(organizationId, end);
        ContentScope scope = contentScope.resolve(organizationId, courseId, members, end);
        if (scope.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = scope.participants(members);

        List<Topic> topics =
                topicRepository
                        .findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                                scope.versionIds())
                        .stream()
                        .sorted(scope.topicOrder())
                        .toList();
        Map<UUID, UUID> versionOfTopic =
                topics.stream().collect(Collectors.toMap(Topic::getId, this::versionOf));
        Map<UUID, ModuleRow> rowOfTopic = new HashMap<>();
        List<ModuleRow> rows = new ArrayList<>();
        for (CourseScope course : scope.courses()) {
            UUID referenceId = course.reference().getId();
            Map<Integer, ModuleRow> byPosition = new TreeMap<>();
            List<Topic> ofCourse =
                    topics.stream()
                            .filter(t -> isOf(course, scope.courseOf(versionOf(t))))
                            .sorted(
                                    Comparator.comparing(
                                                    (Topic t) -> !versionOf(t).equals(referenceId))
                                            .thenComparingInt(Topic::getOrder))
                            .toList();
            for (Topic topic : ofCourse) {
                ModuleRow row =
                        byPosition.computeIfAbsent(
                                topic.getOrder(),
                                order ->
                                        new ModuleRow(
                                                course.course().getId(),
                                                course.course().getName(),
                                                topic,
                                                scope.lessonsByTopic()
                                                        .getOrDefault(topic.getId(), 0L)));
                rowOfTopic.put(topic.getId(), row);
            }
            rows.addAll(byPosition.values());
        }

        if (!userIds.isEmpty()) {
            for (UserTopicStateView state :
                    metricsRepository.findTopicStates(userIds, scope.versionIds(), end)) {
                ModuleRow row = rowOfTopic.get(state.getTopicId());
                if (row == null
                        || !scope.isAssigned(
                                state.getUserId(), versionOfTopic.get(state.getTopicId()))) {
                    continue;
                }
                if (state.getCompleted() > 0) {
                    row.completed++;
                } else {
                    row.inProgress++;
                }
            }
            for (TopicAttemptStatsView attempts :
                    scopedActivity.statsByTopic(
                            organizationId, scope, userIds, period.start(), end)) {
                ModuleRow row = rowOfTopic.get(attempts.getTopicId());
                if (row != null) {
                    row.evaluated += attempts.getEvaluated();
                    row.correct += attempts.getCorrect();
                }
            }
        }

        return rows.stream()
                .map(row -> row.toResponse(scope.participantsOf(row.courseId, userIds).size()))
                .toList();
    }

    private static boolean isOf(CourseScope course, Course other) {
        return other != null && course.course().getId().equals(other.getId());
    }

    private UUID versionOf(Topic topic) {
        return topic.getCourseVersion().getId();
    }

    /** One module of the panel, accumulating every version's equivalent topic. */
    private static final class ModuleRow {
        private final UUID courseId;
        private final String courseName;
        private final Topic topic;
        private final long lessons;
        private long completed;
        private long inProgress;
        private long evaluated;
        private long correct;

        ModuleRow(UUID courseId, String courseName, Topic topic, long lessons) {
            this.courseId = courseId;
            this.courseName = courseName;
            this.topic = topic;
            this.lessons = lessons;
        }

        ModuleStatsResponse toResponse(long participants) {
            return new ModuleStatsResponse(
                    courseId,
                    courseName,
                    topic.getId(),
                    topic.getTitle(),
                    topic.getOrder(),
                    lessons,
                    completed,
                    inProgress,
                    MetricMath.percentage(completed, participants),
                    evaluated,
                    MetricMath.percentage(correct, evaluated));
        }
    }

    /**
     * Day-by-day activity of the participants in the period (inclusive), in Argentina's calendar;
     * the whole history starts on the day the organization was created. Learning minutes come from
     * the daily activity log, which is not split by organization or course.
     */
    @Transactional(readOnly = true)
    public OrganizationActivityResponse getActivity(
            User actor, UUID organizationId, UUID courseId, MetricsPeriod period) {
        accessService.requireManage(actor, organizationId);
        Organization organization =
                organizationRepository
                        .findById(organizationId)
                        .orElseThrow(() -> new NotFoundException("Organization not found"));
        LocalDate to = period.lastDay();
        LocalDate from =
                period.isOpen()
                        ? min(ArgentinaTime.dayOf(organization.getCreatedAt()), to)
                        : period.from();
        Instant start = from.atStartOfDay(ArgentinaTime.ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ArgentinaTime.ZONE).toInstant();
        List<UUID> members = participantIds(organizationId, end);
        ContentScope scope = contentScope.resolve(organizationId, courseId, members, end);
        List<UUID> userIds = scope.participants(members);

        Map<LocalDate, DayTally> days = new TreeMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.put(day, new DayTally());
        }
        DayTally total = new DayTally();
        if (!userIds.isEmpty()) {
            for (AttemptView attempt :
                    scopedActivity.attemptsBetween(organizationId, scope, userIds, start, end)) {
                DayTally day = days.get(ArgentinaTime.dayOf(attempt.getAttemptedAt()));
                if (day != null) {
                    day.add(attempt);
                    total.add(attempt);
                }
            }
            for (Instant completedAt :
                    scopedActivity.lessonCompletionsBetween(scope, userIds, start, end)) {
                DayTally day = days.get(ArgentinaTime.dayOf(completedAt));
                if (day != null) {
                    day.lessons++;
                    total.lessons++;
                }
            }
            for (DailyMinutesView minutes : metricsRepository.findDailyMinutes(userIds, from, to)) {
                DayTally day = days.get(minutes.getDay());
                if (day != null) {
                    day.minutes += minutes.getMinutes();
                    total.minutes += minutes.getMinutes();
                }
            }
        }

        return new OrganizationActivityResponse(
                from,
                to,
                total.figures(),
                days.entrySet().stream()
                        .map(e -> new DailyActivity(e.getKey(), e.getValue().figures()))
                        .toList());
    }

    /** Running totals for one day (or the whole period). */
    private static final class DayTally {
        private final Set<UUID> people = new HashSet<>();
        private long exercises;
        private long correct;
        private long lessons;
        private long minutes;

        void add(AttemptView attempt) {
            people.add(attempt.getUserId());
            if (attempt.getIsCorrect() != null) {
                exercises++;
                if (attempt.getIsCorrect()) {
                    correct++;
                }
            }
        }

        ActivityFigures figures() {
            return new ActivityFigures(
                    people.size(),
                    exercises,
                    correct,
                    MetricMath.percentage(correct, exercises),
                    lessons,
                    minutes);
        }
    }

    /**
     * Active members who had already joined when the period ended; the participants are those of
     * them who take a course in scope ({@link ContentScope#participants}).
     */
    private List<UUID> participantIds(UUID organizationId, Instant end) {
        return memberRepository
                .findByOrganizationIdAndRoleAndStatus(
                        organizationId, MemberRole.MEMBER, MemberStatus.ACTIVE)
                .stream()
                .filter(m -> m.getJoinedAt() == null || m.getJoinedAt().isBefore(end))
                .map(m -> m.getUser().getId())
                .toList();
    }

    private static LocalDate min(LocalDate a, LocalDate b) {
        return a.isBefore(b) ? a : b;
    }

    /** The whole history charts the last weeks; a period charts its own weeks. */
    private static LocalDate firstWeek(MetricsPeriod period) {
        return period.isOpen()
                ? weekStart(LocalDate.now(ArgentinaTime.ZONE)).minusWeeks(EVOLUTION_WEEKS - 1L)
                : weekStart(period.from());
    }

    private static LocalDate lastWeek(MetricsPeriod period) {
        return period.isOpen()
                ? weekStart(LocalDate.now(ArgentinaTime.ZONE))
                : weekStart(period.to());
    }

    private static Instant evolutionStart(MetricsPeriod period) {
        Instant firstWeek = firstWeek(period).atStartOfDay(ArgentinaTime.ZONE).toInstant();
        return period.isOpen() || firstWeek.isAfter(period.start()) ? firstWeek : period.start();
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Buckets daily figures into weeks, keeping empty weeks so a chart has no gaps. */
    private static List<WeeklyPerformanceResponse> evolution(
            List<DailyAttemptStatsView> days, MetricsPeriod period) {
        Map<LocalDate, long[]> byWeek = new HashMap<>();
        for (DailyAttemptStatsView day : days) {
            long[] totals = byWeek.computeIfAbsent(weekStart(day.getDay()), w -> new long[2]);
            totals[0] += day.getEvaluated();
            totals[1] += day.getCorrect();
        }

        List<WeeklyPerformanceResponse> result = new ArrayList<>();
        LocalDate last = lastWeek(period);
        for (LocalDate week = firstWeek(period); !week.isAfter(last); week = week.plusWeeks(1)) {
            long[] totals = byWeek.getOrDefault(week, new long[2]);
            result.add(
                    new WeeklyPerformanceResponse(
                            week, totals[0], MetricMath.percentage(totals[1], totals[0])));
        }
        return result;
    }

    private void requireOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new NotFoundException("Organization not found");
        }
    }
}
