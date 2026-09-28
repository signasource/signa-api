package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
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
import com.signasource.signa_api.organizations.repository.projection.VersionCountView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.entity.User;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Participant list and individual progress for the organization panel. */
@Service
@RequiredArgsConstructor
public class OrganizationMemberQueryService {

    private static final int ACTIVE_DAYS_WINDOW = 30;

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationRepository organizationRepository;
    private final OrganizationMetricsRepository metricsRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationContentScope contentScope;

    @Transactional(readOnly = true)
    public Page<OrganizationMemberSummaryResponse> getMembers(
            User actor, UUID organizationId, String query, MemberStatus status, Pageable pageable) {
        accessService.requireManage(actor, organizationId);
        requireOrganization(organizationId);

        List<MemberStatus> statuses =
                status == null
                        ? List.of(MemberStatus.ACTIVE, MemberStatus.REMOVED)
                        : List.of(status);
        Page<OrganizationMember> page =
                memberRepository.search(
                        organizationId, MemberRole.MEMBER, statuses, toPattern(query), pageable);
        if (page.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, page.getTotalElements());
        }

        List<UUID> userIds = page.getContent().stream().map(m -> m.getUser().getId()).toList();
        ContentScope scope = contentScope.resolve(organizationId);
        Map<UUID, UserAttemptStatsView> attempts =
                indexBy(
                        metricsRepository.findAttemptStatsByUser(organizationId, userIds),
                        UserAttemptStatsView::getUserId);
        Map<UUID, Long> completed = completedLessons(userIds, scope);
        Map<UUID, String> currentModules = currentModules(userIds, scope);

        return page.map(
                member -> {
                    UUID userId = member.getUser().getId();
                    UserAttemptStatsView stats = attempts.get(userId);
                    return new OrganizationMemberSummaryResponse(
                            userId,
                            member.getUser().getName(),
                            member.getUser().getLastName(),
                            member.getUser().getEmail(),
                            member.getStatus(),
                            member.getJoinedAt(),
                            stats == null ? null : stats.getLastActivityAt(),
                            MetricMath.percentage(
                                    completed.getOrDefault(userId, 0L), scope.totalLessons()),
                            currentModules.get(userId));
                });
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
                metricsRepository.findAttemptStatsByUser(organizationId, userIds).stream()
                        .findFirst()
                        .orElse(null);
        long evaluated = attempts == null ? 0 : attempts.getEvaluated();
        long correct = attempts == null ? 0 : attempts.getCorrect();

        List<MemberCourseProgressResponse> courses = List.of();
        long completedLessons = 0;
        long modulesCompleted = 0;
        long signsLearned = 0;
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

        return new MemberProgressResponse(
                userId,
                user.getName(),
                user.getLastName(),
                user.getEmail(),
                member.getStatus(),
                member.getJoinedAt(),
                attempts == null ? null : attempts.getLastActivityAt(),
                MetricMath.percentage(completedLessons, scope.totalLessons()),
                completedLessons,
                scope.totalLessons(),
                modulesCompleted,
                currentModules(userIds, scope).get(userId),
                evaluated,
                correct,
                MetricMath.percentage(correct, evaluated),
                signsLearned,
                streak,
                minutes,
                activeDays,
                courses);
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

    /** The most recently started topic each user still has in progress. */
    private Map<UUID, String> currentModules(List<UUID> userIds, ContentScope scope) {
        Map<UUID, String> result = new HashMap<>();
        if (scope.isEmpty()) {
            return result;
        }
        for (UserTopicProgress progress :
                metricsRepository.findInProgressTopics(userIds, scope.versionIds())) {
            result.putIfAbsent(progress.getUser().getId(), progress.getTopic().getTitle());
        }
        return result;
    }

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
