package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.dto.MemberActivityStatus;
import com.signasource.signa_api.organizations.dto.MemberCourseProgressResponse;
import com.signasource.signa_api.organizations.dto.MemberProgressResponse;
import com.signasource.signa_api.organizations.dto.OrganizationMemberSummaryResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.projection.TopicStatusCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.repository.projection.UserTopicView;
import com.signasource.signa_api.organizations.repository.projection.VersionCountView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Participant list and individual progress for the organization panel. */
@Service
@RequiredArgsConstructor
public class OrganizationMemberQueryService {

    private static final int ACTIVE_DAYS_WINDOW = 30;

    private static final Map<String, Comparator<OrganizationMemberSummaryResponse>> SORTS =
            Map.of(
                    "name",
                    Comparator.comparing(
                                    (OrganizationMemberSummaryResponse m) -> m.name().toLowerCase())
                            .thenComparing(m -> m.lastName().toLowerCase()),
                    "progress",
                    Comparator.comparingInt(OrganizationMemberSummaryResponse::progressPercentage),
                    "lastActivity",
                    Comparator.comparing(
                            OrganizationMemberSummaryResponse::lastActivityAt,
                            Comparator.nullsFirst(Comparator.naturalOrder())),
                    "joinedAt",
                    Comparator.comparing(OrganizationMemberSummaryResponse::joinedAt));

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMetricsRepository metricsRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationContentScope contentScope;
    private final TopicRepository topicRepository;

    /**
     * Progress and activity are computed before sorting and paging, because both can be sorted and
     * filtered by. An organization is bounded by its member limit, so the whole roster fits.
     *
     * @param courseId measures progress against that course only; {@code null} for every course
     * @param activity keeps only participants in that state; {@code null} keeps everyone
     */
    @Transactional(readOnly = true)
    public Page<OrganizationMemberSummaryResponse> getMembers(
            User actor,
            UUID organizationId,
            String query,
            MemberStatus status,
            MemberActivityStatus activity,
            UUID courseId,
            Pageable pageable) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);
        Comparator<OrganizationMemberSummaryResponse> order = comparator(pageable.getSort());
        ContentScope scope = contentScope.resolve(organizationId, courseId);

        List<MemberStatus> statuses =
                status == null
                        ? List.of(MemberStatus.ACTIVE, MemberStatus.REMOVED)
                        : List.of(status);
        List<OrganizationMember> members =
                memberRepository.search(
                        organizationId, MemberRole.MEMBER, statuses, toPattern(query));

        List<OrganizationMemberSummaryResponse> rows =
                summaries(organizationId, members, scope).stream()
                        .filter(m -> activity == null || m.activityStatus() == activity)
                        .sorted(order)
                        .toList();

        int from = (int) Math.min(pageable.getOffset(), rows.size());
        int to = Math.min(from + pageable.getPageSize(), rows.size());
        return new PageImpl<>(rows.subList(from, to), pageable, rows.size());
    }

    @Transactional(readOnly = true)
    public MemberProgressResponse getMemberProgress(User actor, UUID organizationId, UUID userId) {
        accessService.requireManage(actor, organizationId);

        OrganizationMember member =
                memberRepository
                        .findByOrganizationIdAndUserId(organizationId, userId)
                        .filter(m -> m.getRole() == MemberRole.MEMBER)
                        .orElseThrow(() -> new NotFoundException("Member not found"));
        User user = member.getUser();
        List<UUID> userIds = List.of(userId);
        ContentScope scope = contentScope.resolve(organizationId);

        UserAttemptStatsView attempts =
                metricsRepository
                        .findAttemptStatsByUser(organizationId, userIds, scope.courseIds())
                        .stream()
                        .findFirst()
                        .orElse(null);
        long evaluated = attempts == null ? 0 : attempts.getEvaluated();
        long correct = attempts == null ? 0 : attempts.getCorrect();
        Instant lastActivity = attempts == null ? null : attempts.getLastActivityAt();

        List<MemberCourseProgressResponse> courses = List.of();
        long completedLessons = 0;
        long modulesCompleted = 0;
        long signsLearned = 0;
        CurrentModule currentModule = null;
        if (!scope.isEmpty()) {
            Map<UUID, Long> completedByVersion =
                    metricsRepository
                            .findCompletedLessonCountsByVersion(userId, scope.versionIds())
                            .stream()
                            .collect(
                                    Collectors.toMap(
                                            VersionCountView::getVersionId,
                                            VersionCountView::getTotal));
            courses = courseProgress(scope, completedByVersion);
            completedLessons =
                    completedByVersion.values().stream().mapToLong(Long::longValue).sum();
            modulesCompleted =
                    metricsRepository.findTopicStatusCounts(userIds, scope.versionIds()).stream()
                            .filter(c -> c.getStatus() == ProgressStatus.COMPLETED)
                            .mapToLong(TopicStatusCountView::getUsers)
                            .sum();
            for (CourseVersion version : scope.versions()) {
                signsLearned +=
                        userLearnedSignRepository.countByUserIdAndCourseVersionId(
                                userId, version.getId());
            }
            currentModule = currentModules(userIds, scope).get(userId);
        }

        long minutes =
                metricsRepository.findLearningMinutesByUser(userIds).stream()
                        .mapToLong(UserCountView::getTotal)
                        .sum();
        long activeDays =
                metricsRepository
                        .findActiveDaysByUser(
                                userIds, LocalDate.now().minusDays(ACTIVE_DAYS_WINDOW))
                        .stream()
                        .mapToLong(UserCountView::getTotal)
                        .sum();
        int streak =
                userStatsRepository.findByUserId(userId).map(UserStats::getCurrentStreak).orElse(0);
        int progress = MetricMath.percentage(completedLessons, scope.totalLessons());

        return new MemberProgressResponse(
                userId,
                user.getName(),
                user.getLastName(),
                user.getEmail(),
                member.getStatus(),
                activityStatus(member.getStatus(), progress, lastActivity),
                member.getJoinedAt(),
                lastActivity,
                progress,
                completedLessons,
                scope.totalLessons(),
                modulesCompleted,
                currentModule == null ? null : currentModule.module(),
                evaluated,
                correct,
                MetricMath.percentage(correct, evaluated),
                signsLearned,
                streak,
                minutes,
                activeDays,
                courses);
    }

    /**
     * Same rule the overview uses for "active": an attempt within the last {@link
     * OrganizationMetricsService#ACTIVE_WINDOW_DAYS} days.
     */
    static MemberActivityStatus activityStatus(
            MemberStatus status, int progressPercentage, Instant lastActivity) {
        if (status == MemberStatus.REMOVED) {
            return MemberActivityStatus.REMOVED;
        }
        if (progressPercentage >= 100) {
            return MemberActivityStatus.COMPLETED;
        }
        if (lastActivity == null) {
            return MemberActivityStatus.NOT_STARTED;
        }
        Instant activeSince =
                Instant.now().minus(OrganizationMetricsService.ACTIVE_WINDOW_DAYS, ChronoUnit.DAYS);
        return lastActivity.isAfter(activeSince)
                ? MemberActivityStatus.ACTIVE
                : MemberActivityStatus.INACTIVE;
    }

    private List<OrganizationMemberSummaryResponse> summaries(
            UUID organizationId, List<OrganizationMember> members, ContentScope scope) {
        if (members.isEmpty()) {
            return List.of();
        }
        List<UUID> userIds = members.stream().map(m -> m.getUser().getId()).toList();
        Map<UUID, UserAttemptStatsView> attempts =
                indexBy(
                        metricsRepository.findAttemptStatsByUser(
                                organizationId, userIds, scope.courseIds()),
                        UserAttemptStatsView::getUserId);
        Map<UUID, Long> completed = completedLessons(userIds, scope);
        Map<UUID, CurrentModule> currentModules = currentModules(userIds, scope);

        return members.stream()
                .map(
                        member -> {
                            UUID userId = member.getUser().getId();
                            UserAttemptStatsView stats = attempts.get(userId);
                            Instant lastActivity = stats == null ? null : stats.getLastActivityAt();
                            int progress =
                                    MetricMath.percentage(
                                            completed.getOrDefault(userId, 0L),
                                            scope.totalLessons());
                            CurrentModule current = currentModules.get(userId);
                            return new OrganizationMemberSummaryResponse(
                                    userId,
                                    member.getUser().getName(),
                                    member.getUser().getLastName(),
                                    member.getUser().getEmail(),
                                    member.getStatus(),
                                    member.getJoinedAt(),
                                    lastActivity,
                                    progress,
                                    current == null ? null : current.module(),
                                    current == null ? null : current.course(),
                                    activityStatus(member.getStatus(), progress, lastActivity));
                        })
                .toList();
    }

    /** Only the first order is honored; unknown properties are rejected instead of ignored. */
    private static Comparator<OrganizationMemberSummaryResponse> comparator(Sort sort) {
        Sort.Order order = sort.stream().findFirst().orElse(Sort.Order.asc("name"));
        Comparator<OrganizationMemberSummaryResponse> comparator = SORTS.get(order.getProperty());
        if (comparator == null) {
            throw new InvalidInputException("Cannot sort participants by " + order.getProperty());
        }
        comparator = order.isAscending() ? comparator : comparator.reversed();
        return comparator.thenComparing(SORTS.get("name"));
    }

    private List<MemberCourseProgressResponse> courseProgress(
            ContentScope scope, Map<UUID, Long> completedByVersion) {
        return scope.versions().stream()
                .map(
                        version -> {
                            long total = scope.lessonsByVersion().getOrDefault(version.getId(), 0L);
                            long done = completedByVersion.getOrDefault(version.getId(), 0L);
                            return new MemberCourseProgressResponse(
                                    version.getCourse().getId(),
                                    version.getCourse().getName(),
                                    total,
                                    done,
                                    MetricMath.percentage(done, total));
                        })
                .toList();
    }

    private Map<UUID, Long> completedLessons(List<UUID> userIds, ContentScope scope) {
        if (scope.isEmpty()) {
            return Map.of();
        }
        return metricsRepository
                .findCompletedLessonCountsByUser(userIds, scope.versionIds())
                .stream()
                .collect(
                        Collectors.toMap(
                                UserCompletedLessonsView::getUserId,
                                UserCompletedLessonsView::getCompletedLessons));
    }

    /**
     * The most recently started topic each user still has in progress, and its course. Someone who
     * finished a module and hasn't opened the next one yet gets that next module instead of none.
     */
    private Map<UUID, CurrentModule> currentModules(List<UUID> userIds, ContentScope scope) {
        Map<UUID, CurrentModule> result = new HashMap<>();
        if (scope.isEmpty()) {
            return result;
        }
        Map<UUID, String> courseNames =
                scope.versions().stream()
                        .collect(
                                Collectors.toMap(
                                        CourseVersion::getId, v -> v.getCourse().getName()));
        for (UserTopicProgress progress :
                metricsRepository.findInProgressTopics(userIds, scope.versionIds())) {
            result.putIfAbsent(
                    progress.getUser().getId(), module(progress.getTopic(), courseNames));
        }

        List<UUID> betweenModules = userIds.stream().filter(id -> !result.containsKey(id)).toList();
        if (betweenModules.isEmpty()) {
            return result;
        }
        Map<UUID, Set<UUID>> completedTopics =
                metricsRepository.findCompletedTopics(betweenModules, scope.versionIds()).stream()
                        .collect(
                                Collectors.groupingBy(
                                        UserTopicView::getUserId,
                                        Collectors.mapping(
                                                UserTopicView::getTopicId, Collectors.toSet())));
        if (completedTopics.isEmpty()) {
            return result;
        }
        List<Topic> topics =
                topicRepository
                        .findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                                scope.versionIds())
                        .stream()
                        .sorted(scope.topicOrder())
                        .toList();
        completedTopics.forEach(
                (userId, done) ->
                        topics.stream()
                                .filter(t -> !done.contains(t.getId()))
                                .findFirst()
                                .ifPresent(next -> result.put(userId, module(next, courseNames))));
        return result;
    }

    private static CurrentModule module(Topic topic, Map<UUID, String> courseNames) {
        return new CurrentModule(
                topic.getTitle(), courseNames.get(topic.getCourseVersion().getId()));
    }

    private record CurrentModule(String module, String course) {}

    private static <T> Map<UUID, T> indexBy(List<T> values, Function<T, UUID> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity()));
    }

    /** Wildcards are stripped so user input can't widen the LIKE match. */
    private static String toPattern(String query) {
        if (query == null || query.isBlank()) {
            return "%";
        }
        return "%" + query.trim().toLowerCase().replaceAll("[%_\\\\]", "") + "%";
    }

    private void requireOrganization(UUID organizationId) {
        if (!organizationRepository.existsById(organizationId)) {
            throw new NotFoundException("Organization not found");
        }
    }
}
