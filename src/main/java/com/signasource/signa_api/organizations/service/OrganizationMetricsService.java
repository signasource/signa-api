package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse.Participation;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse.Performance;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse.Progress;
import com.signasource.signa_api.organizations.dto.WeeklyPerformanceResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicStatusCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.entity.User;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Organization-wide participation, progress and performance indicators. */
@Service
@RequiredArgsConstructor
public class OrganizationMetricsService {

    static final int ACTIVE_WINDOW_DAYS = 7;
    private static final int ACTIVE_DAYS_WINDOW = 30;
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
    public OrganizationOverviewResponse getOverview(User actor, UUID organizationId) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);

        List<UUID> userIds = participantIds(organizationId);
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (userIds.isEmpty()) {
            return new OrganizationOverviewResponse(
                    new Participation(0, 0, 0, 0, ACTIVE_WINDOW_DAYS, 0, 0, 0),
                    new Progress(0, 0, 0, 0, 0, 0, 0),
                    new Performance(0, 0, 0, 0, evolution(List.of(), today)));
        }

        ContentScope scope = contentScope.resolve(organizationId);
        long participants = userIds.size();

        List<UserAttemptStatsView> attemptStats =
                metricsRepository.findAttemptStatsByUser(organizationId, userIds);
        Instant activeSince = Instant.now().minus(ACTIVE_WINDOW_DAYS, ChronoUnit.DAYS);
        long active =
                attemptStats.stream()
                        .filter(s -> s.getLastActivityAt().isAfter(activeSince))
                        .count();
        long exerciseAttempts =
                attemptStats.stream().mapToLong(UserAttemptStatsView::getEvaluated).sum();
        long correct = attemptStats.stream().mapToLong(UserAttemptStatsView::getCorrect).sum();

        List<Integer> streaks = metricsRepository.findCurrentStreaks(userIds);
        long activeDays =
                metricsRepository
                        .findActiveDaysByUser(userIds, today.minusDays(ACTIVE_DAYS_WINDOW))
                        .stream()
                        .mapToLong(UserCountView::getTotal)
                        .sum();
        long minutes =
                metricsRepository.findLearningMinutesByUser(userIds).stream()
                        .mapToLong(UserCountView::getTotal)
                        .sum();

        Participation participation =
                new Participation(
                        participants,
                        active,
                        participants - active,
                        attemptStats.size(),
                        ACTIVE_WINDOW_DAYS,
                        MetricMath.average(activeDays, participants),
                        streaks.stream().filter(s -> s > 0).count(),
                        streaks.stream().mapToInt(Integer::intValue).max().orElse(0));

        List<Long> completedPerUser = List.of();
        long modulesCompleted = 0;
        if (!scope.isEmpty()) {
            completedPerUser =
                    metricsRepository
                            .findCompletedLessonCountsByUser(userIds, scope.versionIds())
                            .stream()
                            .map(UserCompletedLessonsView::getCompletedLessons)
                            .toList();
            modulesCompleted =
                    metricsRepository.findTopicStatusCounts(userIds, scope.versionIds()).stream()
                            .filter(c -> c.getStatus() == ProgressStatus.COMPLETED)
                            .mapToLong(TopicStatusCountView::getUsers)
                            .sum();
        }
        long completedLessons = completedPerUser.stream().mapToLong(Long::longValue).sum();
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
                        completedLessons,
                        Math.max(0, participants * scope.totalLessons() - completedLessons),
                        modulesCompleted,
                        finishedEverything,
                        minutes,
                        MetricMath.average(minutes, participants));

        AttemptTotalsView recognition =
                metricsRepository.findAttemptTotalsByBlockTypes(
                        organizationId, userIds, SIGN_RECOGNITION_TYPES);
        Performance performance =
                new Performance(
                        exerciseAttempts,
                        MetricMath.percentage(correct, exerciseAttempts),
                        recognition.getEvaluated(),
                        MetricMath.percentage(recognition.getCorrect(), recognition.getEvaluated()),
                        evolution(
                                metricsRepository.findDailyAttemptStats(
                                        organizationId, userIds, evolutionStart(today)),
                                today));

        return new OrganizationOverviewResponse(participation, progress, performance);
    }

    @Transactional(readOnly = true)
    public List<ModuleStatsResponse> getModuleStats(User actor, UUID organizationId) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);

        ContentScope scope = contentScope.resolve(organizationId);
        List<UUID> userIds = participantIds(organizationId);
        if (scope.isEmpty()) {
            return List.of();
        }

        Map<UUID, Long> completedByTopic = new HashMap<>();
        Map<UUID, Long> inProgressByTopic = new HashMap<>();
        Map<UUID, TopicAttemptStatsView> attemptsByTopic = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (TopicStatusCountView count :
                    metricsRepository.findTopicStatusCounts(userIds, scope.versionIds())) {
                if (count.getStatus() == ProgressStatus.COMPLETED) {
                    completedByTopic.put(count.getTopicId(), count.getUsers());
                } else if (count.getStatus() == ProgressStatus.IN_PROGRESS) {
                    inProgressByTopic.put(count.getTopicId(), count.getUsers());
                }
            }
            attemptsByTopic =
                    metricsRepository.findAttemptStatsByTopic(organizationId, userIds).stream()
                            .collect(
                                    Collectors.toMap(
                                            TopicAttemptStatsView::getTopicId,
                                            Function.identity()));
        }

        List<ModuleStatsResponse> result = new ArrayList<>();
        for (Topic topic :
                topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        scope.versionIds())) {
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

    private List<UUID> participantIds(UUID organizationId) {
        return memberRepository
                .findByOrganizationIdAndRoleAndStatus(
                        organizationId, MemberRole.MEMBER, MemberStatus.ACTIVE)
                .stream()
                .map(m -> m.getUser().getId())
                .toList();
    }

    private static Instant evolutionStart(LocalDate today) {
        return weekStart(today)
                .minusWeeks(EVOLUTION_WEEKS - 1L)
                .atStartOfDay()
                .toInstant(ZoneOffset.UTC);
    }

    private static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Buckets daily figures into the last weeks, keeping empty weeks so a chart has no gaps. */
    private static List<WeeklyPerformanceResponse> evolution(
            List<DailyAttemptStatsView> days, LocalDate today) {
        Map<LocalDate, long[]> byWeek = new HashMap<>();
        for (DailyAttemptStatsView day : days) {
            long[] totals = byWeek.computeIfAbsent(weekStart(day.getDay()), w -> new long[2]);
            totals[0] += day.getEvaluated();
            totals[1] += day.getCorrect();
        }

        LocalDate firstWeek = weekStart(today).minusWeeks(EVOLUTION_WEEKS - 1L);
        List<WeeklyPerformanceResponse> result = new ArrayList<>();
        for (int i = 0; i < EVOLUTION_WEEKS; i++) {
            LocalDate week = firstWeek.plusWeeks(i);
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
