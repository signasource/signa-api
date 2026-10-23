package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse.ActivityFigures;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.dto.WeeklyPerformanceResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.AttemptView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.DailyMinutesView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicStatusCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationMetricsServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private OrganizationMetricsRepository metricsRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationContentScope contentScope;

    @InjectMocks private OrganizationMetricsService metricsService;

    private User actor;
    private User ana;
    private User bruno;
    private UUID organizationId;
    private CourseVersion version;
    private ContentScope scope;
    private Topic topic;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(UUID.randomUUID());
        organizationId = UUID.randomUUID();
        ana = userWithId();
        bruno = userWithId();

        Course course = Course.builder().id(UUID.randomUUID()).name("LSA para Salud").build();
        version = CourseVersion.builder().id(UUID.randomUUID()).course(course).build();
        topic =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .title("Greetings")
                        .order(1)
                        .courseVersion(version)
                        .build();
        scope =
                new ContentScope(
                        List.of(version),
                        Map.of(version.getId(), 10L),
                        Map.of(topic.getId(), 10L),
                        10);
    }

    private static User userWithId() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private void stubParticipants(User... users) {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organizationId, MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(
                        java.util.Arrays.stream(users)
                                .map(u -> OrganizationMember.builder().user(u).build())
                                .toList());
    }

    private UserAttemptStatsView attemptStats(Instant lastActivity, long evaluated, long correct) {
        UserAttemptStatsView view = mock(UserAttemptStatsView.class);
        lenient().when(view.getLastActivityAt()).thenReturn(lastActivity);
        lenient().when(view.getEvaluated()).thenReturn(evaluated);
        lenient().when(view.getCorrect()).thenReturn(correct);
        return view;
    }

    private UserCompletedLessonsView completed(long lessons) {
        UserCompletedLessonsView view = mock(UserCompletedLessonsView.class);
        lenient().when(view.getCompletedLessons()).thenReturn(lessons);
        return view;
    }

    private TopicStatusCountView statusCount(ProgressStatus status, long users, UUID topicId) {
        TopicStatusCountView view = mock(TopicStatusCountView.class);
        lenient().when(view.getStatus()).thenReturn(status);
        if (status != ProgressStatus.LOCKED) {
            lenient().when(view.getUsers()).thenReturn(users);
        }
        if (topicId != null) {
            lenient().when(view.getTopicId()).thenReturn(topicId);
        }
        return view;
    }

    private UserCountView count(long total) {
        UserCountView view = mock(UserCountView.class);
        lenient().when(view.getTotal()).thenReturn(total);
        return view;
    }

    @Test
    void shouldReturnAnEmptyOverviewWhenTheOrganizationHasNoParticipants() {
        stubParticipants();

        OrganizationOverviewResponse result =
                metricsService.getOverview(actor, organizationId, null);

        verify(accessService).requireManage(actor, organizationId);
        assertEquals(0, result.participation().totalParticipants());
        assertEquals(0, result.progress().averageProgressPercentage());
        assertEquals(0, result.performance().exerciseAttempts());
        assertEquals(8, result.performance().weeklyEvolution().size());
        verifyNoInteractions(metricsRepository);
    }

    @Test
    void shouldComputeTheOverview() {
        stubParticipants(ana, bruno);
        List<UUID> ids = List.of(ana.getId(), bruno.getId());
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        DailyAttemptStatsView todayStats = mock(DailyAttemptStatsView.class);
        when(todayStats.getDay()).thenReturn(today);
        when(todayStats.getEvaluated()).thenReturn(10L);
        when(todayStats.getCorrect()).thenReturn(8L);
        AttemptTotalsView recognition = mock(AttemptTotalsView.class);
        when(recognition.getEvaluated()).thenReturn(4L);
        when(recognition.getCorrect()).thenReturn(1L);

        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        UserAttemptStatsView recentStats =
                attemptStats(Instant.now().minus(1, ChronoUnit.DAYS), 6, 5);
        UserAttemptStatsView staleStats =
                attemptStats(Instant.now().minus(30, ChronoUnit.DAYS), 4, 3);
        when(metricsRepository.findAttemptStatsByUser(organizationId, ids, scope.courseIds()))
                .thenReturn(List.of(recentStats, staleStats));
        when(metricsRepository.findCurrentStreaks(ids)).thenReturn(List.of(5, 0));
        UserCountView tenDays = count(10);
        UserCountView fiveDays = count(5);
        when(metricsRepository.findActiveDaysByUser(eq(ids), any()))
                .thenReturn(List.of(tenDays, fiveDays));
        UserCountView sixtyMinutes = count(60);
        UserCountView thirtyMinutes = count(30);
        when(metricsRepository.findLearningMinutesByUser(ids))
                .thenReturn(List.of(sixtyMinutes, thirtyMinutes));
        UserCompletedLessonsView allDone = completed(10);
        UserCompletedLessonsView halfDone = completed(5);
        when(metricsRepository.findCompletedLessonCountsByUser(ids, scope.versionIds()))
                .thenReturn(List.of(allDone, halfDone));
        TopicStatusCountView completedTopics = statusCount(ProgressStatus.COMPLETED, 3, null);
        TopicStatusCountView inProgressTopics = statusCount(ProgressStatus.IN_PROGRESS, 1, null);
        when(metricsRepository.findTopicStatusCounts(ids, scope.versionIds()))
                .thenReturn(List.of(completedTopics, inProgressTopics));
        when(metricsRepository.findAttemptTotalsByBlockTypes(
                        eq(organizationId), eq(ids), any(), any()))
                .thenReturn(recognition);
        when(metricsRepository.findDailyAttemptStats(eq(organizationId), eq(ids), any(), any()))
                .thenReturn(List.of(todayStats));

        OrganizationOverviewResponse result =
                metricsService.getOverview(actor, organizationId, null);

        assertEquals(2, result.participation().totalParticipants());
        assertEquals(1, result.participation().activeParticipants());
        assertEquals(1, result.participation().inactiveParticipants());
        assertEquals(2, result.participation().participantsStarted());
        assertEquals(7.5, result.participation().averageActiveDaysLast30());
        assertEquals(1, result.participation().participantsWithStreak());
        assertEquals(5, result.participation().longestCurrentStreak());

        assertEquals(75, result.progress().averageProgressPercentage());
        assertEquals(15, result.progress().completedLessons());
        assertEquals(5, result.progress().pendingLessons());
        assertEquals(3, result.progress().modulesCompleted());
        assertEquals(1, result.progress().participantsCompletedAll());
        assertEquals(90, result.progress().totalLearningMinutes());
        assertEquals(45.0, result.progress().averageLearningMinutes());

        assertEquals(10, result.performance().exerciseAttempts());
        assertEquals(80, result.performance().correctPercentage());
        assertEquals(4, result.performance().signRecognitionAttempts());
        assertEquals(25, result.performance().signRecognitionCorrectPercentage());

        List<WeeklyPerformanceResponse> weeks = result.performance().weeklyEvolution();
        assertEquals(8, weeks.size());
        WeeklyPerformanceResponse last = weeks.get(7);
        assertEquals(10, last.exerciseAttempts());
        assertEquals(80, last.correctPercentage());
        assertEquals(0, weeks.get(0).exerciseAttempts());
        assertTrue(weeks.get(0).weekStart().isBefore(last.weekStart()));
    }

    @Test
    void shouldReportZeroProgressWhenNoCourseIsContracted() {
        stubParticipants(ana);
        List<UUID> ids = List.of(ana.getId());
        AttemptTotalsView none = mock(AttemptTotalsView.class);
        when(none.getEvaluated()).thenReturn(0L);
        when(none.getCorrect()).thenReturn(0L);

        when(contentScope.resolve(organizationId, null))
                .thenReturn(new ContentScope(List.of(), Map.of(), Map.of(), 0));
        when(metricsRepository.findAttemptStatsByUser(eq(organizationId), eq(ids), any()))
                .thenReturn(List.of());
        when(metricsRepository.findCurrentStreaks(ids)).thenReturn(List.of());
        when(metricsRepository.findActiveDaysByUser(eq(ids), any())).thenReturn(List.of());
        when(metricsRepository.findLearningMinutesByUser(ids)).thenReturn(List.of());
        when(metricsRepository.findAttemptTotalsByBlockTypes(
                        eq(organizationId), eq(ids), any(), any()))
                .thenReturn(none);
        when(metricsRepository.findDailyAttemptStats(eq(organizationId), eq(ids), any(), any()))
                .thenReturn(List.of());

        OrganizationOverviewResponse result =
                metricsService.getOverview(actor, organizationId, null);

        assertEquals(1, result.participation().totalParticipants());
        assertEquals(0, result.participation().activeParticipants());
        assertEquals(0, result.progress().averageProgressPercentage());
        assertEquals(0, result.progress().pendingLessons());
        assertEquals(0, result.progress().participantsCompletedAll());
        assertEquals(0, result.performance().correctPercentage());
    }

    @Test
    void shouldThrowNotFoundForOverviewOfUnknownOrganization() {
        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> metricsService.getOverview(actor, organizationId, null));
    }

    @Test
    void shouldComputePerModuleStats() {
        stubParticipants(ana, bruno);
        List<UUID> ids = List.of(ana.getId(), bruno.getId());
        TopicAttemptStatsView attempts = mock(TopicAttemptStatsView.class);
        when(attempts.getTopicId()).thenReturn(topic.getId());
        when(attempts.getEvaluated()).thenReturn(20L);
        when(attempts.getCorrect()).thenReturn(15L);

        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        TopicStatusCountView topicCompleted =
                statusCount(ProgressStatus.COMPLETED, 1, topic.getId());
        TopicStatusCountView topicInProgress =
                statusCount(ProgressStatus.IN_PROGRESS, 1, topic.getId());
        TopicStatusCountView topicLocked = statusCount(ProgressStatus.LOCKED, 0, null);
        when(metricsRepository.findTopicStatusCounts(ids, scope.versionIds()))
                .thenReturn(List.of(topicCompleted, topicInProgress, topicLocked));
        when(metricsRepository.findAttemptStatsByTopic(organizationId, ids, scope.courseIds()))
                .thenReturn(List.of(attempts));
        when(topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        scope.versionIds()))
                .thenReturn(List.of(topic));

        List<ModuleStatsResponse> result =
                metricsService.getModuleStats(actor, organizationId, null);

        assertEquals(1, result.size());
        ModuleStatsResponse module = result.get(0);
        assertEquals("LSA para Salud", module.courseName());
        assertEquals("Greetings", module.title());
        assertEquals(10, module.totalLessons());
        assertEquals(1, module.participantsCompleted());
        assertEquals(1, module.participantsInProgress());
        assertEquals(50, module.completionPercentage());
        assertEquals(20, module.exerciseAttempts());
        assertEquals(75, module.correctPercentage());
    }

    @Test
    void shouldListModulesInTheOrderTheCoursesWereContracted() {
        stubParticipants();
        Course second = Course.builder().id(UUID.randomUUID()).name("Atención").build();
        CourseVersion secondVersion =
                CourseVersion.builder().id(UUID.randomUUID()).course(second).build();
        Topic secondTopic =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .title("Saludar")
                        .order(1)
                        .courseVersion(secondVersion)
                        .build();
        Topic laterTopic =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .title("Numbers")
                        .order(2)
                        .courseVersion(version)
                        .build();
        ContentScope twoCourses =
                new ContentScope(List.of(secondVersion, version), Map.of(), Map.of(), 0);
        when(contentScope.resolve(organizationId, null)).thenReturn(twoCourses);
        when(topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        twoCourses.versionIds()))
                .thenReturn(List.of(laterTopic, topic, secondTopic));

        List<ModuleStatsResponse> result =
                metricsService.getModuleStats(actor, organizationId, null);

        assertEquals(
                List.of("Saludar", "Greetings", "Numbers"),
                result.stream().map(ModuleStatsResponse::title).toList());
    }

    @Test
    void shouldListModulesWithZeroesWhenThereAreNoParticipants() {
        stubParticipants();
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        when(topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        scope.versionIds()))
                .thenReturn(List.of(topic));

        List<ModuleStatsResponse> result =
                metricsService.getModuleStats(actor, organizationId, null);

        assertEquals(1, result.size());
        assertEquals(0, result.get(0).participantsCompleted());
        assertEquals(0, result.get(0).completionPercentage());
        assertEquals(0, result.get(0).correctPercentage());
        verifyNoInteractions(metricsRepository);
    }

    @Test
    void shouldReturnNoModulesWhenNoCourseIsContracted() {
        stubParticipants(ana);
        when(contentScope.resolve(organizationId, null))
                .thenReturn(new ContentScope(List.of(), Map.of(), Map.of(), 0));

        assertTrue(metricsService.getModuleStats(actor, organizationId, null).isEmpty());
        verifyNoInteractions(topicRepository, metricsRepository);
    }

    @Test
    void shouldThrowNotFoundForModulesOfUnknownOrganization() {
        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () -> metricsService.getModuleStats(actor, organizationId, null));
    }

    private AttemptView attempt(User user, Instant at, Boolean correct) {
        AttemptView view = mock(AttemptView.class);
        lenient().when(view.getUserId()).thenReturn(user.getId());
        lenient().when(view.getAttemptedAt()).thenReturn(at);
        lenient().when(view.getIsCorrect()).thenReturn(correct);
        return view;
    }

    private DailyMinutesView minutes(LocalDate day, long total) {
        DailyMinutesView view = mock(DailyMinutesView.class);
        lenient().when(view.getDay()).thenReturn(day);
        lenient().when(view.getMinutes()).thenReturn(total);
        return view;
    }

    @Test
    void shouldBucketActivityByArgentineDay() {
        stubParticipants(ana, bruno);
        List<UUID> ids = List.of(ana.getId(), bruno.getId());
        LocalDate monday = LocalDate.of(2026, 10, 5);
        LocalDate sunday = monday.plusDays(6);
        ZoneId argentina = ZoneId.of("America/Argentina/Cordoba");
        // 23:30 in Argentina is already the next day in UTC: it must count on Monday.
        Instant mondayNight = monday.atTime(23, 30).atZone(argentina).toInstant();
        Instant tuesdayMorning = monday.plusDays(1).atTime(9, 0).atZone(argentina).toInstant();
        when(contentScope.resolve(organizationId, version.getCourse().getId())).thenReturn(scope);
        List<AttemptView> attempts =
                List.of(
                        attempt(ana, mondayNight, true),
                        attempt(ana, mondayNight, null),
                        attempt(bruno, tuesdayMorning, false),
                        attempt(ana, tuesdayMorning, true),
                        attempt(ana, sunday.plusDays(1).atStartOfDay(argentina).toInstant(), true));
        when(metricsRepository.findAttemptsBetween(
                        eq(organizationId), eq(ids), eq(scope.courseIds()), any(), any()))
                .thenReturn(attempts);
        when(metricsRepository.findLessonCompletionsBetween(
                        eq(ids), eq(scope.versionIds()), any(), any()))
                .thenReturn(List.of(tuesdayMorning, tuesdayMorning));
        List<DailyMinutesView> dailyMinutes =
                List.of(minutes(monday, 25), minutes(sunday.plusDays(3), 99));
        when(metricsRepository.findDailyMinutes(ids, monday, sunday)).thenReturn(dailyMinutes);

        OrganizationActivityResponse result =
                metricsService.getActivity(
                        actor, organizationId, version.getCourse().getId(), monday, sunday);

        verify(accessService).requireManage(actor, organizationId);
        assertEquals(7, result.days().size());
        ActivityFigures mondayFigures = result.days().get(0).figures();
        assertEquals(monday, result.days().get(0).date());
        assertEquals(1, mondayFigures.activeParticipants());
        assertEquals(1, mondayFigures.exerciseAttempts());
        assertEquals(100, mondayFigures.correctPercentage());
        assertEquals(25, mondayFigures.learningMinutes());
        ActivityFigures tuesdayFigures = result.days().get(1).figures();
        assertEquals(2, tuesdayFigures.activeParticipants());
        assertEquals(2, tuesdayFigures.exerciseAttempts());
        assertEquals(50, tuesdayFigures.correctPercentage());
        assertEquals(2, tuesdayFigures.lessonsCompleted());
        assertEquals(0, result.days().get(6).figures().exerciseAttempts());

        assertEquals(2, result.totals().activeParticipants());
        assertEquals(3, result.totals().exerciseAttempts());
        assertEquals(2, result.totals().correctAnswers());
        assertEquals(67, result.totals().correctPercentage());
        assertEquals(2, result.totals().lessonsCompleted());
        assertEquals(25, result.totals().learningMinutes());
    }

    @Test
    void shouldReturnEmptyDaysWhenThereAreNoParticipants() {
        stubParticipants();
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        LocalDate day = LocalDate.of(2026, 10, 1);

        OrganizationActivityResponse result =
                metricsService.getActivity(actor, organizationId, null, day, day.plusDays(2));

        assertEquals(3, result.days().size());
        assertEquals(0, result.totals().activeParticipants());
        verifyNoInteractions(metricsRepository);
    }

    @Test
    void shouldRejectAnInvalidOrTooLongPeriod() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        LocalDate day = LocalDate.of(2026, 10, 1);

        assertThrows(
                InvalidInputException.class,
                () ->
                        metricsService.getActivity(
                                actor, organizationId, null, day, day.minusDays(1)));
        assertThrows(
                InvalidInputException.class,
                () ->
                        metricsService.getActivity(
                                actor,
                                organizationId,
                                null,
                                day,
                                day.plusDays(OrganizationMetricsService.MAX_ACTIVITY_DAYS)));
        verifyNoInteractions(contentScope, metricsRepository);
    }

    @Test
    void shouldThrowNotFoundForActivityOfUnknownOrganization() {
        when(organizationRepository.existsById(organizationId)).thenReturn(false);
        LocalDate day = LocalDate.of(2026, 10, 1);

        assertThrows(
                NotFoundException.class,
                () -> metricsService.getActivity(actor, organizationId, null, day, day));
    }
}
