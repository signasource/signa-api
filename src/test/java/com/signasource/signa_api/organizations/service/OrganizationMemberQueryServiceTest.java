package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.dto.MemberActivityStatus;
import com.signasource.signa_api.organizations.dto.MemberProgressResponse;
import com.signasource.signa_api.organizations.dto.OrganizationMemberSummaryResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.projection.TopicProgressCountView;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceTest {

    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMetricsRepository metricsRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private UserLearnedSignRepository userLearnedSignRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationContentScope contentScope;
    @Mock private TopicRepository topicRepository;

    @InjectMocks private OrganizationMemberQueryService queryService;

    private final Pageable pageable = PageRequest.of(0, 20);

    private User actor;
    private User ana;
    private UUID organizationId;
    private Course course;
    private CourseVersion version;
    private ContentScope scope;
    private OrganizationMember anaMember;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(UUID.randomUUID());
        organizationId = UUID.randomUUID();

        ana = new User();
        ana.setId(UUID.randomUUID());
        ana.setName("Ana");
        ana.setLastName("Perez");
        ana.setEmail("ana@hospital.com");

        course = Course.builder().id(UUID.randomUUID()).name("LSA para Salud").build();
        version = CourseVersion.builder().id(UUID.randomUUID()).course(course).build();
        scope =
                new ContentScope(
                        List.of(version),
                        Map.of(version.getId(), 10L),
                        Map.of(UUID.randomUUID(), 10L),
                        10);

        anaMember =
                OrganizationMember.builder()
                        .user(ana)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .joinedAt(Instant.parse("2026-09-01T00:00:00Z"))
                        .build();
    }

    private UserAttemptStatsView attemptStats(
            UUID userId, Instant lastActivity, long evaluated, long correct) {
        UserAttemptStatsView view = mock(UserAttemptStatsView.class);
        lenient().when(view.getUserId()).thenReturn(userId);
        lenient().when(view.getLastActivityAt()).thenReturn(lastActivity);
        if (evaluated >= 0) {
            lenient().when(view.getEvaluated()).thenReturn(evaluated);
            lenient().when(view.getCorrect()).thenReturn(correct);
        }
        return view;
    }

    private UserTopicProgress inProgress(User user, String title) {
        return UserTopicProgress.builder()
                .user(user)
                .topic(Topic.builder().title(title).courseVersion(version).build())
                .status(ProgressStatus.IN_PROGRESS)
                .build();
    }

    private OrganizationMember member(User user, Instant joinedAt) {
        return OrganizationMember.builder()
                .user(user)
                .role(MemberRole.MEMBER)
                .status(MemberStatus.ACTIVE)
                .joinedAt(joinedAt)
                .build();
    }

    private User user(String name, String lastName) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setLastName(lastName);
        user.setEmail(name.toLowerCase() + "@hospital.com");
        return user;
    }

    private UserCompletedLessonsView completedLessons(UUID userId, long lessons) {
        UserCompletedLessonsView view = mock(UserCompletedLessonsView.class);
        lenient().when(view.getUserId()).thenReturn(userId);
        lenient().when(view.getCompletedLessons()).thenReturn(lessons);
        return view;
    }

    @Test
    void shouldListMembersWithProgressLastActivityAndCurrentModule() {
        Instant lastActivity = Instant.now().minus(1, ChronoUnit.DAYS);
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(eq(organizationId), eq(MemberRole.MEMBER), any(), eq("%ana%")))
                .thenReturn(List.of(anaMember));
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        UserAttemptStatsView anaStats = attemptStats(ana.getId(), lastActivity, 0, 0);
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId),
                        eq(List.of(ana.getId())),
                        eq(scope.courseIds()),
                        any(),
                        any()))
                .thenReturn(List.of(anaStats));
        UserCompletedLessonsView anaLessons = completedLessons(ana.getId(), 4);
        when(metricsRepository.findCompletedLessonCountsByUser(
                        eq(List.of(ana.getId())), eq(scope.versionIds()), any()))
                .thenReturn(List.of(anaLessons));
        UserTopicProgress greetings = inProgress(ana, "Greetings");
        when(metricsRepository.findInProgressTopics(
                        eq(List.of(ana.getId())), eq(scope.versionIds()), any()))
                .thenReturn(List.of(greetings));

        Page<OrganizationMemberSummaryResponse> result =
                queryService.getMembers(
                        actor,
                        organizationId,
                        "  Ana ",
                        MemberStatus.ACTIVE,
                        null,
                        null,
                        MetricsPeriod.ALL_TIME,
                        pageable);

        verify(accessService).requireManage(actor, organizationId);
        OrganizationMemberSummaryResponse row = result.getContent().get(0);
        assertEquals(ana.getId(), row.userId());
        assertEquals("Ana", row.name());
        assertEquals("ana@hospital.com", row.email());
        assertEquals(MemberStatus.ACTIVE, row.status());
        assertEquals(lastActivity, row.lastActivityAt());
        assertEquals(40, row.progressPercentage());
        assertEquals("Greetings", row.currentModule());
        assertEquals("LSA para Salud", row.currentCourse());
        assertEquals(MemberActivityStatus.ACTIVE, row.activityStatus());
    }

    @Test
    void shouldSortFilterAndPageByComputedMetrics() {
        User bruno = user("Bruno", "Diaz");
        User carla = user("Carla", "Sosa");
        List<OrganizationMember> members =
                List.of(
                        anaMember,
                        member(bruno, Instant.parse("2026-09-05T00:00:00Z")),
                        member(carla, Instant.parse("2026-08-01T00:00:00Z")));
        List<UUID> ids = List.of(ana.getId(), bruno.getId(), carla.getId());
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any())).thenReturn(members);
        when(contentScope.resolve(organizationId, course.getId())).thenReturn(scope);
        UserAttemptStatsView anaStats = attemptStats(ana.getId(), Instant.now(), 0, 0);
        UserAttemptStatsView carlaStats =
                attemptStats(carla.getId(), Instant.now().minus(30, ChronoUnit.DAYS), 0, 0);
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId), eq(ids), eq(scope.courseIds()), any(), any()))
                .thenReturn(List.of(anaStats, carlaStats));
        UserCompletedLessonsView anaLessons = completedLessons(ana.getId(), 4);
        UserCompletedLessonsView carlaLessons = completedLessons(carla.getId(), 10);
        when(metricsRepository.findCompletedLessonCountsByUser(
                        eq(ids), eq(scope.versionIds()), any()))
                .thenReturn(List.of(anaLessons, carlaLessons));
        when(metricsRepository.findInProgressTopics(eq(ids), eq(scope.versionIds()), any()))
                .thenReturn(List.of());

        Page<OrganizationMemberSummaryResponse> byProgress =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        null,
                        course.getId(),
                        MetricsPeriod.ALL_TIME,
                        PageRequest.of(0, 2, Sort.by(Sort.Order.desc("progress"))));
        Page<OrganizationMemberSummaryResponse> notStarted =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        MemberActivityStatus.NOT_STARTED,
                        course.getId(),
                        MetricsPeriod.ALL_TIME,
                        pageable);
        Page<OrganizationMemberSummaryResponse> secondPageByActivity =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        null,
                        course.getId(),
                        MetricsPeriod.ALL_TIME,
                        PageRequest.of(1, 2, Sort.by("lastActivity")));

        assertEquals(3, byProgress.getTotalElements());
        assertEquals(2, byProgress.getTotalPages());
        assertEquals(
                List.of("Carla", "Ana"),
                byProgress.getContent().stream().map(m -> m.name()).toList());
        assertEquals(
                MemberActivityStatus.COMPLETED, byProgress.getContent().get(0).activityStatus());
        assertEquals(1, notStarted.getTotalElements());
        assertEquals("Bruno", notStarted.getContent().get(0).name());
        assertEquals(
                List.of("Ana"),
                secondPageByActivity.getContent().stream().map(m -> m.name()).toList());
    }

    @Test
    void shouldShowTheNextModuleForSomeoneBetweenModules() {
        Topic first =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .title("Saludos")
                        .order(1)
                        .courseVersion(version)
                        .build();
        Topic second =
                Topic.builder()
                        .id(UUID.randomUUID())
                        .title("Familia")
                        .order(2)
                        .courseVersion(version)
                        .build();
        UserTopicView done = mock(UserTopicView.class);
        when(done.getUserId()).thenReturn(ana.getId());
        when(done.getTopicId()).thenReturn(first.getId());
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any())).thenReturn(List.of(anaMember));
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        when(metricsRepository.findInProgressTopics(
                        eq(List.of(ana.getId())), eq(scope.versionIds()), any()))
                .thenReturn(List.of());
        when(metricsRepository.findCompletedTopics(
                        eq(List.of(ana.getId())), eq(scope.versionIds()), any()))
                .thenReturn(List.of(done));
        when(topicRepository.findByCourseVersionIdInOrderByCourseVersionIdAscOrderAsc(
                        scope.versionIds()))
                .thenReturn(List.of(second, first));

        OrganizationMemberSummaryResponse row =
                queryService
                        .getMembers(
                                actor,
                                organizationId,
                                null,
                                MemberStatus.ACTIVE,
                                null,
                                null,
                                MetricsPeriod.ALL_TIME,
                                pageable)
                        .getContent()
                        .get(0);

        assertEquals("Familia", row.currentModule());
        assertEquals("LSA para Salud", row.currentCourse());
    }

    @Test
    void shouldSortByNameAndJoiningDate() {
        User bruno = user("bruno", "Diaz");
        OrganizationMember brunoMember = member(bruno, Instant.parse("2026-08-01T00:00:00Z"));
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any()))
                .thenReturn(List.of(brunoMember, anaMember));
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);

        Page<OrganizationMemberSummaryResponse> byName =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        null,
                        null,
                        MetricsPeriod.ALL_TIME,
                        PageRequest.of(0, 20, Sort.by(Sort.Order.desc("name"))));
        Page<OrganizationMemberSummaryResponse> byJoined =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        null,
                        null,
                        MetricsPeriod.ALL_TIME,
                        PageRequest.of(0, 20, Sort.by("joinedAt")));

        assertEquals("bruno", byName.getContent().get(0).name());
        assertEquals("bruno", byJoined.getContent().get(0).name());
        assertEquals(MemberActivityStatus.NOT_STARTED, byName.getContent().get(1).activityStatus());
    }

    @Test
    void shouldRejectSortingByAnUnknownField() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);

        assertThrows(
                InvalidInputException.class,
                () ->
                        queryService.getMembers(
                                actor,
                                organizationId,
                                null,
                                MemberStatus.ACTIVE,
                                null,
                                null,
                                MetricsPeriod.ALL_TIME,
                                PageRequest.of(0, 20, Sort.by("email"))));
        verifyNoInteractions(memberRepository);
    }

    @Test
    void shouldReturnAnEmptyPageWhenOffsetIsPastTheEnd() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any())).thenReturn(List.of(anaMember));
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);

        Page<OrganizationMemberSummaryResponse> page =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        null,
                        null,
                        MetricsPeriod.ALL_TIME,
                        PageRequest.of(3, 20));

        assertTrue(page.getContent().isEmpty());
        assertEquals(1, page.getTotalElements());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSearchBothStatusesAndStripWildcardsWhenNoStatusIsGiven() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any())).thenReturn(List.of());

        queryService.getMembers(
                actor,
                organizationId,
                "a%_b\\",
                null,
                null,
                null,
                MetricsPeriod.ALL_TIME,
                pageable);

        ArgumentCaptor<java.util.Collection<MemberStatus>> statuses =
                ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(memberRepository)
                .search(
                        eq(organizationId),
                        eq(MemberRole.MEMBER),
                        statuses.capture(),
                        pattern.capture());
        assertEquals(
                List.of(MemberStatus.ACTIVE, MemberStatus.REMOVED),
                List.copyOf(statuses.getValue()));
        assertEquals("%ab%", pattern.getValue());
    }

    @Test
    void shouldReturnEmptyPageWithoutQueryingMetricsWhenThereAreNoMembers() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), eq("%"))).thenReturn(List.of());

        Page<OrganizationMemberSummaryResponse> result =
                queryService.getMembers(
                        actor,
                        organizationId,
                        " ",
                        MemberStatus.ACTIVE,
                        null,
                        null,
                        MetricsPeriod.ALL_TIME,
                        pageable);

        assertTrue(result.isEmpty());
        verifyNoInteractions(metricsRepository);
    }

    @Test
    void shouldListMembersWithZeroProgressWhenNoCourseIsContracted() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any())).thenReturn(List.of(anaMember));
        when(contentScope.resolve(organizationId, null))
                .thenReturn(new ContentScope(List.of(), Map.of(), Map.of(), 0));
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId), any(), any(), any(), any()))
                .thenReturn(List.of());

        OrganizationMemberSummaryResponse row =
                queryService
                        .getMembers(
                                actor,
                                organizationId,
                                null,
                                MemberStatus.ACTIVE,
                                null,
                                null,
                                MetricsPeriod.ALL_TIME,
                                pageable)
                        .getContent()
                        .get(0);

        assertEquals(0, row.progressPercentage());
        assertNull(row.lastActivityAt());
        assertNull(row.currentModule());
        assertEquals(MemberActivityStatus.NOT_STARTED, row.activityStatus());
    }

    @Test
    void shouldThrowNotFoundListingMembersOfUnknownOrganization() {
        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () ->
                        queryService.getMembers(
                                actor,
                                organizationId,
                                null,
                                MemberStatus.ACTIVE,
                                null,
                                null,
                                MetricsPeriod.ALL_TIME,
                                pageable));
    }

    @Test
    void shouldDeriveTheActivityStatus() {
        Instant recent = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant old = Instant.now().minus(20, ChronoUnit.DAYS);
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        assertEquals(
                MemberActivityStatus.REMOVED,
                OrganizationMemberQueryService.activityStatus(
                        MemberStatus.REMOVED, 100, recent, weekAgo));
        assertEquals(
                MemberActivityStatus.COMPLETED,
                OrganizationMemberQueryService.activityStatus(
                        MemberStatus.ACTIVE, 100, old, weekAgo));
        assertEquals(
                MemberActivityStatus.NOT_STARTED,
                OrganizationMemberQueryService.activityStatus(
                        MemberStatus.ACTIVE, 0, null, weekAgo));
        assertEquals(
                MemberActivityStatus.ACTIVE,
                OrganizationMemberQueryService.activityStatus(
                        MemberStatus.ACTIVE, 0, recent, weekAgo));
        assertEquals(
                MemberActivityStatus.INACTIVE,
                OrganizationMemberQueryService.activityStatus(
                        MemberStatus.ACTIVE, 50, old, weekAgo));
    }

    @Test
    void shouldBuildTheIndividualProgress() {
        Instant lastActivity = Instant.parse("2026-09-20T10:00:00Z");
        VersionCountView completedInVersion = mock(VersionCountView.class);
        when(completedInVersion.getVersionId()).thenReturn(version.getId());
        when(completedInVersion.getTotal()).thenReturn(5L);
        TopicProgressCountView doneModules = mock(TopicProgressCountView.class);
        when(doneModules.getCompleted()).thenReturn(2L);
        UserCountView minutes = mock(UserCountView.class);
        when(minutes.getTotal()).thenReturn(95L);
        UserCountView activeDays = mock(UserCountView.class);
        when(activeDays.getTotal()).thenReturn(6L);
        UserStats stats = UserStats.builder().currentStreak(3).build();

        List<UUID> ids = List.of(ana.getId());
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.of(anaMember));
        when(contentScope.resolve(organizationId)).thenReturn(scope);
        UserAttemptStatsView anaStatsWithAttempts = attemptStats(ana.getId(), lastActivity, 8, 6);
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId), eq(ids), eq(scope.courseIds()), any(), any()))
                .thenReturn(List.of(anaStatsWithAttempts));
        when(metricsRepository.findCompletedLessonCountsByVersion(
                        eq(ana.getId()), eq(scope.versionIds()), any()))
                .thenReturn(List.of(completedInVersion));
        when(metricsRepository.findTopicProgressCounts(eq(ids), eq(scope.versionIds()), any()))
                .thenReturn(List.of(doneModules));
        when(userLearnedSignRepository.countByUserIdAndCourseVersionIdAndLearnedAtBefore(
                        eq(ana.getId()), eq(version.getId()), any()))
                .thenReturn(7L);
        when(metricsRepository.findLearningMinutesByUser(eq(ids), any(), any()))
                .thenReturn(List.of(minutes));
        when(metricsRepository.findActiveDaysByUser(eq(ids), any(), any()))
                .thenReturn(List.of(activeDays));
        when(userStatsRepository.findByUserId(ana.getId())).thenReturn(Optional.of(stats));
        when(metricsRepository.findInProgressTopics(eq(ids), eq(scope.versionIds()), any()))
                .thenReturn(List.of(inProgress(ana, "Numbers")));

        MemberProgressResponse result =
                queryService.getMemberProgress(
                        actor, organizationId, ana.getId(), MetricsPeriod.ALL_TIME);

        verify(accessService).requireManage(actor, organizationId);
        assertEquals("Ana", result.name());
        assertEquals(50, result.progressPercentage());
        assertEquals(5, result.completedLessons());
        assertEquals(10, result.totalLessons());
        assertEquals(2, result.modulesCompleted());
        assertEquals("Numbers", result.currentModule());
        assertEquals(8, result.exerciseAttempts());
        assertEquals(6, result.correctAnswers());
        assertEquals(75, result.correctPercentage());
        assertEquals(7, result.signsLearned());
        assertEquals(3, result.currentStreak());
        assertEquals(95, result.learningMinutes());
        assertEquals(6, result.activeDaysLast30());
        assertEquals(lastActivity, result.lastActivityAt());
        assertEquals(MemberActivityStatus.INACTIVE, result.activityStatus());
        assertEquals(1, result.courses().size());
        assertEquals("LSA para Salud", result.courses().get(0).courseName());
        assertEquals(50, result.courses().get(0).progressPercentage());
    }

    @Test
    void shouldReturnZeroedProgressForAMemberWithNoActivityAndNoContractedCourses() {
        List<UUID> ids = List.of(ana.getId());
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.of(anaMember));
        when(contentScope.resolve(organizationId))
                .thenReturn(new ContentScope(List.of(), Map.of(), Map.of(), 0));
        when(metricsRepository.findAttemptStatsByUser(
                        eq(organizationId), eq(ids), any(), any(), any()))
                .thenReturn(List.of());
        when(metricsRepository.findLearningMinutesByUser(eq(ids), any(), any()))
                .thenReturn(List.of());
        when(metricsRepository.findActiveDaysByUser(eq(ids), any(), any())).thenReturn(List.of());
        when(userStatsRepository.findByUserId(ana.getId())).thenReturn(Optional.empty());

        MemberProgressResponse result =
                queryService.getMemberProgress(
                        actor, organizationId, ana.getId(), MetricsPeriod.ALL_TIME);

        assertEquals(0, result.progressPercentage());
        assertEquals(0, result.exerciseAttempts());
        assertEquals(0, result.correctPercentage());
        assertEquals(0, result.currentStreak());
        assertEquals(0, result.learningMinutes());
        assertNull(result.lastActivityAt());
        assertNull(result.currentModule());
        assertEquals(MemberActivityStatus.NOT_STARTED, result.activityStatus());
        assertTrue(result.courses().isEmpty());
    }

    @Test
    void shouldThrowNotFoundForUnknownMember() {
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        queryService.getMemberProgress(
                                actor, organizationId, ana.getId(), MetricsPeriod.ALL_TIME));
    }

    @Test
    void shouldNotExposeProgressOfOrganizationAdmins() {
        anaMember.setRole(MemberRole.ADMIN);
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.of(anaMember));

        assertThrows(
                NotFoundException.class,
                () ->
                        queryService.getMemberProgress(
                                actor, organizationId, ana.getId(), MetricsPeriod.ALL_TIME));
        verifyNoInteractions(metricsRepository);
    }

    @Test
    void shouldListOnlyPeopleWhoHadJoinedAndRateThemWithinThePeriod() {
        MetricsPeriod period =
                MetricsPeriod.of(LocalDate.of(2026, 9, 7), LocalDate.of(2026, 9, 20));
        User bruno = user("Bruno", "Diaz");
        User carla = user("Carla", "Sosa");
        List<OrganizationMember> members =
                List.of(
                        anaMember,
                        member(bruno, Instant.parse("2026-09-02T00:00:00Z")),
                        member(carla, Instant.parse("2026-10-01T00:00:00Z")));
        List<UUID> ids = List.of(ana.getId(), bruno.getId());
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any())).thenReturn(members);
        when(contentScope.resolve(organizationId, null)).thenReturn(scope);
        UserAttemptStatsView anaStats =
                attemptStats(ana.getId(), period.start().plus(1, ChronoUnit.DAYS), 0, 0);
        UserAttemptStatsView brunoStats =
                attemptStats(bruno.getId(), period.start().minus(1, ChronoUnit.DAYS), 0, 0);
        when(metricsRepository.findAttemptStatsByUser(
                        organizationId, ids, scope.courseIds(), period.start(), period.end()))
                .thenReturn(List.of(anaStats, brunoStats));
        when(metricsRepository.findCompletedLessonCountsByUser(
                        ids, scope.versionIds(), period.end()))
                .thenReturn(List.of());
        when(metricsRepository.findInProgressTopics(ids, scope.versionIds(), period.end()))
                .thenReturn(List.of());

        Page<OrganizationMemberSummaryResponse> result =
                queryService.getMembers(
                        actor,
                        organizationId,
                        null,
                        MemberStatus.ACTIVE,
                        null,
                        null,
                        period,
                        pageable);

        assertEquals(
                List.of("Ana", "Bruno"), result.getContent().stream().map(m -> m.name()).toList());
        assertEquals(MemberActivityStatus.ACTIVE, result.getContent().get(0).activityStatus());
        assertEquals(MemberActivityStatus.INACTIVE, result.getContent().get(1).activityStatus());
    }

    @Test
    void shouldMeasureIndividualActivityWithinThePeriod() {
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        MetricsPeriod period = MetricsPeriod.of(from, to);
        List<UUID> ids = List.of(ana.getId());
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.of(anaMember));
        when(contentScope.resolve(organizationId)).thenReturn(scope);
        when(userStatsRepository.findByUserId(ana.getId())).thenReturn(Optional.empty());

        queryService.getMemberProgress(actor, organizationId, ana.getId(), period);

        verify(metricsRepository)
                .findAttemptStatsByUser(
                        organizationId, ids, scope.courseIds(), period.start(), period.end());
        verify(metricsRepository)
                .findCompletedLessonCountsByVersion(ana.getId(), scope.versionIds(), period.end());
        verify(metricsRepository).findLearningMinutesByUser(ids, from, to);
        verify(metricsRepository).findActiveDaysByUser(ids, from, to);
        verify(userLearnedSignRepository)
                .countByUserIdAndCourseVersionIdAndLearnedAtBefore(
                        ana.getId(), version.getId(), period.end());
    }
}
