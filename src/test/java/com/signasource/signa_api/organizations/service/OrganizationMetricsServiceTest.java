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

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.dto.ModuleStatsResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse;
import com.signasource.signa_api.organizations.dto.OrganizationActivityResponse.ActivityFigures;
import com.signasource.signa_api.organizations.dto.OrganizationOverviewResponse;
import com.signasource.signa_api.organizations.dto.WeeklyPerformanceResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
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
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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

    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Cordoba");
    private static final Instant CREATED_AT =
            LocalDate.now(ARGENTINA).minusDays(3).atTime(12, 0).atZone(ARGENTINA).toInstant();

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
        stubMembers(
                java.util.Arrays.stream(users)
                        .map(u -> OrganizationMember.builder().user(u).build())
                        .toArray(OrganizationMember[]::new));
    }

    private void stubMembers(OrganizationMember... members) {
        lenient().when(organizationRepository.existsById(organizationId)).thenReturn(true);
        lenient()
                .when(organizationRepository.findById(organizationId))
                .thenReturn(
                        Optional.of(
                                Organization.builder()
                                        .id(organizationId)
                                        .createdAt(CREATED_AT)
                                        .build()));
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organizationId, MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(List.of(members));
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

    private TopicProgressCountView progressCount(UUID topicId, long completed, long inProgress) {
        TopicProgressCountView view = mock(TopicProgressCountView.class);
        lenient().when(view.getTopicId()).thenReturn(topicId);
        lenient().when(view.getCompleted()).thenReturn(completed);
        lenient().when(view.getInProgress()).thenReturn(inProgress);
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
                metricsService.getOverview(actor, organizationId, null, MetricsPeriod.ALL_TIME);

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
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId), eq(ids), eq(scope.courseIds()), any(), any()))
                .thenReturn(List.of(recentStats, staleStats));
        when(metricsRepository.findCurrentStreaks(ids)).thenReturn(List.of(5, 0));
        UserCountView tenDays = count(10);
        UserCountView fiveDays = count(5);
        when(metricsRepository.findActiveDaysByUser(eq(ids), any(), any()))
                .thenReturn(List.of(tenDays, fiveDays));
        UserCountView sixtyMinutes = count(60);
        UserCountView thirtyMinutes = count(30);
        when(metricsRepository.findLearningMinutesByUser(eq(ids), any(), any()))
                .thenReturn(List.of(sixtyMinutes, thirtyMinutes));
        UserCompletedLessonsView allDone = completed(10);
        UserCompletedLessonsView halfDone = completed(5);
        when(metricsRepository.findCompletedLessonCountsByUser(
                        eq(ids), eq(scope.versionIds()), any()))
                .thenReturn(List.of(allDone, halfDone));
        when(metricsRepository.findLessonCompletionsBetween(
                        eq(ids), eq(scope.versionIds()), any(), any()))
                .thenReturn(List.of(Instant.now(), Instant.now(), Instant.now(), Instant.now()));
        when(metricsRepository.countTopicsCompletedBetween(
                        eq(ids), eq(scope.versionIds()), any(), any()))
                .thenReturn(3L);
        when(metricsRepository.findAttemptTotalsByBlockTypes(
                        eq(organizationId), eq(ids), any(), any(), any(), any()))
                .thenReturn(recognition);
        when(metricsRepository.findDailyAttemptStats(
                        eq(organizationId), eq(ids), any(), any(), any()))
                .thenReturn(List.of(todayStats));

        OrganizationOverviewResponse result =
                metricsService.getOverview(actor, organizationId, null, MetricsPeriod.ALL_TIME);

        assertEquals(2, result.participation().totalParticipants());
        assertEquals(1, result.participation().activeParticipants());
        assertEquals(1, result.participation().inactiveParticipants());
        assertEquals(2, result.participation().participantsStarted());
        assertEquals(7.5, result.participation().averageActiveDaysLast30());
        assertEquals(1, result.participation().participantsWithStreak());
        assertEquals(5, result.participation().longestCurrentStreak());

        assertEquals(75, result.progress().averageProgressPercentage());
        assertEquals(4, result.progress().completedLessons());
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
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId), eq(ids), any(), any(), any()))
                .thenReturn(List.of());
        when(metricsRepository.findCurrentStreaks(ids)).thenReturn(List.of());
        when(metricsRepository.findActiveDaysByUser(eq(ids), any(), any())).thenReturn(List.of());
        when(metricsRepository.findLearningMinutesByUser(eq(ids), any(), any()))
                .thenReturn(List.of());
        when(metricsRepository.findAttemptTotalsByBlockTypes(
                        eq(organizationId), eq(ids), any(), any(), any(), any()))
                .thenReturn(none);
        when(metricsRepository.findDailyAttemptStats(
                        eq(organizationId), eq(ids), any(), any(), any()))
                .thenReturn(List.of());

        OrganizationOverviewResponse result =
                metricsService.getOverview(actor, organizationId, null, MetricsPeriod.ALL_TIME);

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
                () ->
                        metricsService.getOverview(
                                actor, organizationId, null, MetricsPeriod.ALL_TIME));
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
        TopicProgressCountView topicProgress = progressCount(topic.getId(), 1, 1);
        when(metricsRepository.findTopicProgressCounts(eq(ids), eq(scope.versionIds()), any()))
                .thenReturn(List.of(topicProgress));
        when(metricsRepository.findAttemptStatsByTopic(
                        eq(organizationId), eq(ids), eq(scope.courseIds()), any(), any()))
                .thenReturn(List.of(attempts));
        when(topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        scope.versionIds()))
                .thenReturn(List.of(topic));

        List<ModuleStatsResponse> result =
                metricsService.getModuleStats(actor, organizationId, null, MetricsPeriod.ALL_TIME);

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
                metricsService.getModuleStats(actor, organizationId, null, MetricsPeriod.ALL_TIME);

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
                metricsService.getModuleStats(actor, organizationId, null, MetricsPeriod.ALL_TIME);

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

        assertTrue(
                metricsService
                        .getModuleStats(actor, organizationId, null, MetricsPeriod.ALL_TIME)
                        .isEmpty());
        verifyNoInteractions(topicRepository, metricsRepository);
    }

    @Test
    void shouldThrowNotFoundForModulesOfUnknownOrganization() {
        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () ->
                        metricsService.getModuleStats(
                                actor, organizationId, null, MetricsPeriod.ALL_TIME));
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
                        actor,
                        organizationId,
                        version.getCourse().getId(),
                        MetricsPeriod.of(monday, sunday));

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
                metricsService.getActivity(
                        actor, organizationId, null, MetricsPeriod.of(day, day.plusDays(2)));

        assertEquals(3, result.days().size());
        assertEquals(0, result.totals().activeParticipants());
        verifyNoInteractions(metricsRepository);
    }

    @Test
    void shouldThrowNotFoundForActivityOfUnknownOrganization() {
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        metricsService.getActivity(
                                actor, organizationId, null, MetricsPeriod.ALL_TIME));
        verifyNoInteractions(contentScope, metricsRepository);
    }

    @Test
    void shouldChartTheWholeHistoryFromTheDayTheOrganizationWasCreated() {
        stubParticipants();
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);

        OrganizationActivityResponse result =
                metricsService.getActivity(actor, organizationId, null, MetricsPeriod.ALL_TIME);

        LocalDate today = LocalDate.now(ARGENTINA);
        assertEquals(today.minusDays(3), result.from());
        assertEquals(today, result.to());
        assertEquals(4, result.days().size());
    }

    @Test
    void shouldMeasureTheOverviewOverAPeriod() {
        LocalDate from = LocalDate.of(2026, 9, 7);
        LocalDate to = LocalDate.of(2026, 9, 20);
        MetricsPeriod period = MetricsPeriod.of(from, to);
        OrganizationMember joinedLater =
                OrganizationMember.builder()
                        .user(userWithId())
                        .joinedAt(period.end().plus(1, ChronoUnit.DAYS))
                        .build();
        stubMembers(
                OrganizationMember.builder().user(ana).joinedAt(period.start()).build(),
                OrganizationMember.builder().user(bruno).build(),
                joinedLater);
        List<UUID> ids = List.of(ana.getId(), bruno.getId());
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        UserAttemptStatsView inPeriod = attemptStats(period.start().plus(2, ChronoUnit.DAYS), 6, 3);
        UserAttemptStatsView beforePeriod =
                attemptStats(period.start().minus(1, ChronoUnit.DAYS), 0, 0);
        when(metricsRepository.findAttemptStatsByUser(
                        organizationId, ids, scope.courseIds(), period.start(), period.end()))
                .thenReturn(List.of(inPeriod, beforePeriod));
        UserCountView fourDays = count(4);
        UserCountView fiftyMinutes = count(50);
        UserCompletedLessonsView fourLessons = completed(4);
        when(metricsRepository.findActiveDaysByUser(ids, from, to)).thenReturn(List.of(fourDays));
        when(metricsRepository.findLearningMinutesByUser(ids, from, to))
                .thenReturn(List.of(fiftyMinutes));
        when(metricsRepository.findCompletedLessonCountsByUser(
                        ids, scope.versionIds(), period.end()))
                .thenReturn(List.of(fourLessons));
        AttemptTotalsView none = mock(AttemptTotalsView.class);
        when(metricsRepository.findAttemptTotalsByBlockTypes(
                        eq(organizationId),
                        eq(ids),
                        any(),
                        any(),
                        eq(period.start()),
                        eq(period.end())))
                .thenReturn(none);

        OrganizationOverviewResponse result =
                metricsService.getOverview(actor, organizationId, null, period);

        assertEquals(2, result.participation().totalParticipants());
        assertEquals(1, result.participation().activeParticipants());
        assertEquals(2, result.participation().participantsStarted());
        assertEquals(14, result.participation().activeWindowDays());
        assertEquals(2.0, result.participation().averageActiveDaysLast30());
        assertEquals(50, result.progress().totalLearningMinutes());
        assertEquals(16, result.progress().pendingLessons());
        assertEquals(50, result.performance().correctPercentage());
        List<WeeklyPerformanceResponse> weeks = result.performance().weeklyEvolution();
        assertEquals(2, weeks.size());
        assertEquals(from, weeks.get(0).weekStart());
    }

    @Test
    void shouldMeasureModulesAsOfTheEndOfThePeriod() {
        MetricsPeriod period =
                MetricsPeriod.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        stubParticipants(ana);
        List<UUID> ids = List.of(ana.getId());
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        when(topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        scope.versionIds()))
                .thenReturn(List.of(topic));

        metricsService.getModuleStats(actor, organizationId, null, period);

        verify(metricsRepository).findTopicProgressCounts(ids, scope.versionIds(), period.end());
        verify(metricsRepository)
                .findAttemptStatsByTopic(
                        organizationId, ids, scope.courseIds(), period.start(), period.end());
    }
}
