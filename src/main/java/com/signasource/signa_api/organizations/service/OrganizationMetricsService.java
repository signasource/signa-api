package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.BlockType;
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
import com.signasource.signa_api.organizations.repository.projection.TopicProgressCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.Function;
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
    private static final List<BlockType> SIGN_RECOGNITION_TYPES =
            List.of(BlockType.VISUAL_RECOGNITION, BlockType.PERFORM_SIGN);

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository memberRepository;
    private final OrganizationMetricsRepository metricsRepository;
    private final TopicRepository topicRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationContentScope contentScope;

    @Transactional(readOnly = true)
    public OrganizationOverviewResponse getOverview(
            User actor, UUID organizationId, UUID courseId, MetricsPeriod period) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);
        ContentScope scope = contentScope.resolve(organizationId, courseId);

        Instant start = period.start();
        Instant end = period.end();
        List<UUID> userIds = participantIds(organizationId, end);
        if (userIds.isEmpty()) {
            return new OrganizationOverviewResponse(
                    new Participation(0, 0, 0, 0, period.activeWindowDays(), 0, 0, 0),
                    new Progress(0, 0, 0, 0, 0, 0, 0),
                    new Performance(0, 0, 0, 0, evolution(List.of(), period)));
        }

        long participants = userIds.size();

        List<UserAttemptStatsView> attemptStats =
                metricsRepository.findAttemptStatsByUser(
                        organizationId, userIds, scope.courseIds(), start, end);
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
                        .findLearningMinutesByUser(userIds, period.firstDay(), period.lastDay())
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

        List<Long> completedPerUser =
                metricsRepository
                        .findCompletedLessonCountsByUser(userIds, scope.versionIds(), end)
                        .stream()
                        .map(UserCompletedLessonsView::getCompletedLessons)
                        .toList();
        long completedByEnd = completedPerUser.stream().mapToLong(Long::longValue).sum();
        long completedInPeriod =
                metricsRepository
                        .findLessonCompletionsBetween(userIds, scope.versionIds(), start, end)
                        .size();
        long modulesCompleted =
                metricsRepository.countTopicsCompletedBetween(
                        userIds, scope.versionIds(), start, end);
        long percentageSum =
                completedPerUser.stream()
                        .mapToLong(done -> MetricMath.percentage(done, scope.totalLessons()))
                        .sum();
        long finishedEverything =
                scope.totalLessons() == 0
                        ? 0
                        : completedPerUser.stream()
                                .filter(done -> done >= scope.totalLessons())
                                .count();

        Progress progress =
                new Progress(
                        (int) Math.round(percentageSum / (double) participants),
                        completedInPeriod,
                        Math.max(0, participants * scope.totalLessons() - completedByEnd),
                        modulesCompleted,
                        finishedEverything,
                        minutes,
                        MetricMath.average(minutes, participants));

        AttemptTotalsView recognition =
                metricsRepository.findAttemptTotalsByBlockTypes(
                        organizationId,
                        userIds,
                        scope.courseIds(),
                        SIGN_RECOGNITION_TYPES,
                        start,
                        end);
        Performance performance =
                new Performance(
                        exerciseAttempts,
                        MetricMath.percentage(correct, exerciseAttempts),
                        recognition.getEvaluated(),
                        MetricMath.percentage(recognition.getCorrect(), recognition.getEvaluated()),
                        evolution(
                                metricsRepository.findDailyAttemptStats(
                                        organizationId,
                                        userIds,
                                        scope.courseIds(),
                                        evolutionStart(period),
                                        end),
                                period));

        return new OrganizationOverviewResponse(participation, progress, performance);
    }

    /**
     * Per module: who had completed it and who was on it as of the end of the period, and the
     * accuracy of the attempts made during the period.
     */
    @Transactional(readOnly = true)
    public List<ModuleStatsResponse> getModuleStats(
            User actor, UUID organizationId, UUID courseId, MetricsPeriod period) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);

        ContentScope scope = contentScope.resolve(organizationId, courseId);
        Instant end = period.end();
        List<UUID> userIds = participantIds(organizationId, end);
        if (scope.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> completedByTopic = new HashMap<>();
        Map<UUID, Long> inProgressByTopic = new HashMap<>();
        Map<UUID, TopicAttemptStatsView> attemptsByTopic = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (TopicProgressCountView count :
                    metricsRepository.findTopicProgressCounts(userIds, scope.versionIds(), end)) {
                completedByTopic.put(count.getTopicId(), count.getCompleted());
                inProgressByTopic.put(count.getTopicId(), count.getInProgress());
            }
            attemptsByTopic =
                    metricsRepository
                            .findAttemptStatsByTopic(
                                    organizationId, userIds, scope.courseIds(), period.start(), end)
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            TopicAttemptStatsView::getTopicId,
                                            Function.identity()));
        }

        List<ModuleStatsResponse> result = new ArrayList<>();
        List<Topic> topics =
                topicRepository
                        .findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                                scope.versionIds())
                        .stream()
                        .sorted(scope.topicOrder())
                        .toList();
        for (Topic topic : topics) {
            long completed = completedByTopic.getOrDefault(topic.getId(), 0L);
            TopicAttemptStatsView attempts = attemptsByTopic.get(topic.getId());
            long evaluated = attempts == null ? 0 : attempts.getEvaluated();
            result.add(
                    new ModuleStatsResponse(
                            topic.getCourseVersion().getCourse().getId(),
                            topic.getCourseVersion().getCourse().getName(),
                            topic.getId(),
                            topic.getTitle(),
                            topic.getOrder(),
                            scope.lessonsByTopic().getOrDefault(topic.getId(), 0L),
                            completed,
                            inProgressByTopic.getOrDefault(topic.getId(), 0L),
                            MetricMath.percentage(completed, userIds.size()),
                            evaluated,
                            MetricMath.percentage(
                                    attempts == null ? 0 : attempts.getCorrect(), evaluated)));
        }
        return result;
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
        ContentScope scope = contentScope.resolve(organizationId, courseId);
        Instant start = from.atStartOfDay(ArgentinaTime.ZONE).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(ArgentinaTime.ZONE).toInstant();
        List<UUID> userIds = participantIds(organizationId, end);

        Map<LocalDate, DayTally> days = new TreeMap<>();
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            days.put(day, new DayTally());
        }
        DayTally total = new DayTally();
        if (!userIds.isEmpty()) {
            for (AttemptView attempt :
                    metricsRepository.findAttemptsBetween(
                            organizationId, userIds, scope.courseIds(), start, end)) {
                DayTally day = days.get(ArgentinaTime.dayOf(attempt.getAttemptedAt()));
                if (day != null) {
                    day.add(attempt);
                    total.add(attempt);
                }
            }
            for (Instant completedAt :
                    metricsRepository.findLessonCompletionsBetween(
                            userIds, scope.versionIds(), start, end)) {
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

    /** Active participants who had already joined when the period ended. */
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
                ? weekStart(LocalDate.now(ZoneOffset.UTC)).minusWeeks(EVOLUTION_WEEKS - 1L)
                : weekStart(period.from());
    }

    private static LocalDate lastWeek(MetricsPeriod period) {
        return period.isOpen() ? weekStart(LocalDate.now(ZoneOffset.UTC)) : weekStart(period.to());
    }

    private static Instant evolutionStart(MetricsPeriod period) {
        Instant firstWeek = firstWeek(period).atStartOfDay().toInstant(ZoneOffset.UTC);
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
