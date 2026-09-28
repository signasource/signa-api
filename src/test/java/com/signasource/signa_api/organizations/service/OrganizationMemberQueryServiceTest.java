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

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
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
import java.time.Instant;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberQueryServiceTest {

    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMetricsRepository metricsRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private UserLearnedSignRepository userLearnedSignRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationContentScope contentScope;

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
                .topic(Topic.builder().title(title).build())
                .status(ProgressStatus.IN_PROGRESS)
                .build();
    }

    @Test
    void shouldListMembersWithProgressLastActivityAndCurrentModule() {
        Instant lastActivity = Instant.parse("2026-09-20T10:00:00Z");
        UserCompletedLessonsView completed = mock(UserCompletedLessonsView.class);
        when(completed.getUserId()).thenReturn(ana.getId());
        when(completed.getCompletedLessons()).thenReturn(4L);
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(
                        eq(organizationId),
                        eq(MemberRole.MEMBER),
                        any(),
                        eq("%ana%"),
                        eq(pageable)))
                .thenReturn(new PageImpl<>(List.of(anaMember), pageable, 1));
        when(contentScope.resolve(organizationId)).thenReturn(scope);
        UserAttemptStatsView anaStats = attemptStats(ana.getId(), lastActivity, 0, 0);
        when(metricsRepository.findAttemptStatsByUser(organizationId, List.of(ana.getId())))
                .thenReturn(List.of(anaStats));
        when(metricsRepository.findCompletedLessonCountsByUser(
                        List.of(ana.getId()), scope.versionIds()))
                .thenReturn(List.of(completed));
        when(metricsRepository.findInProgressTopics(List.of(ana.getId()), scope.versionIds()))
                .thenReturn(List.of(inProgress(ana, "Greetings")));

        Page<OrganizationMemberSummaryResponse> result =
                queryService.getMembers(
                        actor, organizationId, "  Ana ", MemberStatus.ACTIVE, pageable);

        verify(accessService).requireManage(actor, organizationId);
        OrganizationMemberSummaryResponse row = result.getContent().get(0);
        assertEquals(ana.getId(), row.userId());
        assertEquals("Ana", row.name());
        assertEquals("ana@hospital.com", row.email());
        assertEquals(MemberStatus.ACTIVE, row.status());
        assertEquals(lastActivity, row.lastActivityAt());
        assertEquals(40, row.progressPercentage());
        assertEquals("Greetings", row.currentModule());
    }

    @Test
    @SuppressWarnings("unchecked")
    void shouldSearchBothStatusesAndStripWildcardsWhenNoStatusIsGiven() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        queryService.getMembers(actor, organizationId, "a%_b\\", null, pageable);

        ArgumentCaptor<java.util.Collection<MemberStatus>> statuses =
                ArgumentCaptor.forClass(java.util.Collection.class);
        ArgumentCaptor<String> pattern = ArgumentCaptor.forClass(String.class);
        verify(memberRepository)
                .search(
                        eq(organizationId),
                        eq(MemberRole.MEMBER),
                        statuses.capture(),
                        pattern.capture(),
                        eq(pageable));
        assertEquals(
                List.of(MemberStatus.ACTIVE, MemberStatus.REMOVED),
                List.copyOf(statuses.getValue()));
        assertEquals("%ab%", pattern.getValue());
    }

    @Test
    void shouldReturnEmptyPageWithoutQueryingMetricsWhenThereAreNoMembers() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), eq("%"), any()))
                .thenReturn(new PageImpl<>(List.of(), pageable, 0));

        Page<OrganizationMemberSummaryResponse> result =
                queryService.getMembers(actor, organizationId, " ", MemberStatus.ACTIVE, pageable);

        assertTrue(result.isEmpty());
        verifyNoInteractions(metricsRepository, contentScope);
    }

    @Test
    void shouldListMembersWithZeroProgressWhenNoCourseIsContracted() {
        when(organizationRepository.existsById(organizationId)).thenReturn(true);
        when(memberRepository.search(any(), any(), any(), any(), any()))
                .thenReturn(new PageImpl<>(List.of(anaMember), pageable, 1));
        when(contentScope.resolve(organizationId))
                .thenReturn(new ContentScope(List.of(), Map.of(), Map.of(), 0));
        when(metricsRepository.findAttemptStatsByUser(organizationId, List.of(ana.getId())))
                .thenReturn(List.of());

        OrganizationMemberSummaryResponse row =
                queryService
                        .getMembers(actor, organizationId, null, MemberStatus.ACTIVE, pageable)
                        .getContent()
                        .get(0);

        assertEquals(0, row.progressPercentage());
        assertNull(row.lastActivityAt());
        assertNull(row.currentModule());
    }

    @Test
    void shouldThrowNotFoundListingMembersOfUnknownOrganization() {
        when(organizationRepository.existsById(organizationId)).thenReturn(false);

        assertThrows(
                NotFoundException.class,
                () ->
                        queryService.getMembers(
                                actor, organizationId, null, MemberStatus.ACTIVE, pageable));
    }

    @Test
    void shouldBuildTheIndividualProgress() {
        Instant lastActivity = Instant.parse("2026-09-20T10:00:00Z");
        VersionCountView completedInVersion = mock(VersionCountView.class);
        when(completedInVersion.getVersionId()).thenReturn(version.getId());
        when(completedInVersion.getTotal()).thenReturn(5L);
        TopicStatusCountView doneModules = mock(TopicStatusCountView.class);
        when(doneModules.getStatus()).thenReturn(ProgressStatus.COMPLETED);
        when(doneModules.getUsers()).thenReturn(2L);
        TopicStatusCountView otherModules = mock(TopicStatusCountView.class);
        when(otherModules.getStatus()).thenReturn(ProgressStatus.IN_PROGRESS);
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
        when(metricsRepository.findAttemptStatsByUser(organizationId, ids))
                .thenReturn(List.of(anaStatsWithAttempts));
        when(metricsRepository.findCompletedLessonCountsByVersion(ana.getId(), scope.versionIds()))
                .thenReturn(List.of(completedInVersion));
        when(metricsRepository.findTopicStatusCounts(ids, scope.versionIds()))
                .thenReturn(List.of(doneModules, otherModules));
        when(userLearnedSignRepository.countByUserIdAndCourseVersionId(
                        ana.getId(), version.getId()))
                .thenReturn(7L);
        when(metricsRepository.findLearningMinutesByUser(ids)).thenReturn(List.of(minutes));
        when(metricsRepository.findActiveDaysByUser(eq(ids), any()))
                .thenReturn(List.of(activeDays));
        when(userStatsRepository.findByUserId(ana.getId())).thenReturn(Optional.of(stats));
        when(metricsRepository.findInProgressTopics(ids, scope.versionIds()))
                .thenReturn(List.of(inProgress(ana, "Numbers")));

        MemberProgressResponse result =
                queryService.getMemberProgress(actor, organizationId, ana.getId());

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
        when(metricsRepository.findAttemptStatsByUser(organizationId, ids)).thenReturn(List.of());
        when(metricsRepository.findLearningMinutesByUser(ids)).thenReturn(List.of());
        when(metricsRepository.findActiveDaysByUser(eq(ids), any())).thenReturn(List.of());
        when(userStatsRepository.findByUserId(ana.getId())).thenReturn(Optional.empty());

        MemberProgressResponse result =
                queryService.getMemberProgress(actor, organizationId, ana.getId());

        assertEquals(0, result.progressPercentage());
        assertEquals(0, result.exerciseAttempts());
        assertEquals(0, result.correctPercentage());
        assertEquals(0, result.currentStreak());
        assertEquals(0, result.learningMinutes());
        assertNull(result.lastActivityAt());
        assertNull(result.currentModule());
        assertTrue(result.courses().isEmpty());
    }

    @Test
    void shouldThrowNotFoundForUnknownMember() {
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> queryService.getMemberProgress(actor, organizationId, ana.getId()));
    }

    @Test
    void shouldNotExposeProgressOfOrganizationAdmins() {
        anaMember.setRole(MemberRole.ADMIN);
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, ana.getId()))
                .thenReturn(Optional.of(anaMember));

        assertThrows(
                NotFoundException.class,
                () -> queryService.getMemberProgress(actor, organizationId, ana.getId()));
        verifyNoInteractions(metricsRepository);
    }
}
