package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse.FollowUpParticipant;
import com.signasource.signa_api.organizations.dto.OrganizationHomeResponse.FollowUpReason;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.organizations.repository.projection.AttemptView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.organizations.service.OrganizationContentScope.CourseScope;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationHomeServiceTest {

    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private OrganizationMetricsRepository metricsRepository;
    @Mock private OrganizationContentScope contentScope;
    @Mock private PurchasePolicy policy;

    private OrganizationHomeService service;

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private final Organization organization =
            Organization.builder().id(UUID.randomUUID()).name("Hospital").build();
    private final Course course = Course.builder().id(UUID.randomUUID()).name("LSA Salud").build();
    private final CourseVersion version =
            CourseVersion.builder().id(UUID.randomUUID()).course(course).build();

    private final List<OrganizationMember> members = new ArrayList<>();
    private final List<UserCourseEnrollment> enrollments = new ArrayList<>();
    private final List<PurchaseSeat> seats = new ArrayList<>();
    private final Map<UUID, Map<UUID, Long>> completed = new HashMap<>();
    private final List<UserAttemptStatsView> lastActivity = new ArrayList<>();
    private final List<AttemptView> windowAttempts = new ArrayList<>();

    @BeforeEach
    void setUp() {
        service =
                new OrganizationHomeService(
                        accessService,
                        memberRepository,
                        contentScope,
                        new ScopedActivity(metricsRepository),
                        policy);
        lenient().when(policy.manualReminderCooldown()).thenReturn(Duration.ofHours(24));
    }

    private void stubRepositories() {
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(members);
        ParticipantAccessResolver resolver =
                new ParticipantAccessResolver(seatRepository, enrollmentRepository);
        when(contentScope.resolve(eq(organization.getId()), isNull(), anyCollection(), eq(now)))
                .thenAnswer(
                        call ->
                                new ContentScope(
                                        List.of(new CourseScope(course, version)),
                                        Map.of(version.getId(), version),
                                        Map.of(version.getId(), 10L),
                                        Map.of(),
                                        Map.of(),
                                        completed,
                                        resolver.resolve(
                                                organization.getId(),
                                                call.getArgument(2),
                                                List.of(course.getId()))));
        when(enrollmentRepository.findOrganizationEnrollments(
                        eq(organization.getId()), anyCollection(), eq(List.of(course.getId()))))
                .thenReturn(enrollments);
        when(seatRepository.findAcceptedByOrganizationIdAndUserIdIn(
                        eq(organization.getId()), anyCollection()))
                .thenReturn(seats);
        lenient()
                .when(
                        metricsRepository.findAttemptStatsByUser(
                                eq(organization.getId()),
                                anyCollection(),
                                eq(List.of(course.getId())),
                                eq(now),
                                eq(now)))
                .thenReturn(lastActivity);
        lenient()
                .when(
                        metricsRepository.findAttemptsBetween(
                                eq(organization.getId()),
                                anyCollection(),
                                eq(List.of(course.getId())),
                                eq(now.minus(Duration.ofDays(7))),
                                eq(now)))
                .thenReturn(windowAttempts);
    }

    private User participant(String name) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setName(name);
        user.setLastName("Test");
        members.add(OrganizationMember.builder().user(user).organization(organization).build());
        return user;
    }

    private void enroll(User user, Instant startedAt, Instant expiresAt) {
        enrollments.add(
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .organization(organization)
                        .status(EnrollmentStatus.ENROLLED)
                        .startedAt(startedAt)
                        .accessExpiresAt(expiresAt)
                        .build());
    }

    private void seat(
            User user,
            PurchaseStatus purchaseStatus,
            SeatStatus status,
            Instant activatedAt,
            Instant expiresAt) {
        CoursePurchase purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .course(course)
                        .status(purchaseStatus)
                        .build();
        seats.add(
                PurchaseSeat.builder()
                        .id(UUID.randomUUID())
                        .purchase(purchase)
                        .user(user)
                        .email(user.getName() + "@test.com")
                        .status(status)
                        .activatedAt(activatedAt)
                        .accessExpiresAt(expiresAt)
                        .build());
    }

    private void complete(User user, long lessons) {
        completed.put(user.getId(), Map.of(version.getId(), lessons));
    }

    private void lastActive(User user, Instant at) {
        UserAttemptStatsView view = mock(UserAttemptStatsView.class);
        lenient().when(view.getUserId()).thenReturn(user.getId());
        lenient().when(view.getLastActivityAt()).thenReturn(at);
        lastActivity.add(view);
    }

    private void attempt(User user, Instant at) {
        AttemptView view = mock(AttemptView.class);
        lenient().when(view.getUserId()).thenReturn(user.getId());
        lenient().when(view.getAttemptedAt()).thenReturn(at);
        windowAttempts.add(view);
    }

    private Instant daysAgo(int days) {
        return now.minus(Duration.ofDays(days));
    }

    private Instant inDays(int days) {
        return now.plus(Duration.ofDays(days));
    }

    @Test
    void shouldLeaveOutAnAccessWhoseOnlySeatWasRefunded() {
        User ana = participant("Ana");
        User bruno = participant("Bruno");
        enroll(ana, daysAgo(10), inDays(30));
        seat(ana, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(10), inDays(30));
        enroll(bruno, daysAgo(10), now);
        seat(bruno, PurchaseStatus.REFUNDED, SeatStatus.REVOKED, daysAgo(10), inDays(30));
        complete(ana, 5);
        complete(bruno, 10);
        lastActive(ana, now.minus(Duration.ofHours(1)));
        stubRepositories();

        OrganizationHomeResponse home = service.home(organization.getId(), null, now);

        assertEquals(1, home.participants());
        assertEquals(1, home.startedParticipants());
        assertEquals(50, home.averageProgressPercentage());
        assertEquals(0, home.completedParticipants());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<UUID>> users = ArgumentCaptor.forClass(Collection.class);
        verify(metricsRepository)
                .findAttemptStatsByUser(
                        eq(organization.getId()), users.capture(), any(), any(), any());
        assertEquals(List.of(ana.getId()), List.copyOf(users.getValue()));
    }

    @Test
    void shouldCountAnEnrollmentFromBeforeSeatsExisted() {
        User carla = participant("Carla");
        enroll(carla, daysAgo(60), null);
        complete(carla, 10);
        stubRepositories();

        OrganizationHomeResponse home = service.home(organization.getId(), null, now);

        assertEquals(1, home.participants());
        assertEquals(1, home.completedParticipants());
        assertEquals(100, home.completedPercentage());
        assertTrue(home.followUp().isEmpty());
    }

    @Test
    void shouldAverageProgressOnlyAmongThoseWhoStarted() {
        User ana = participant("Ana");
        User eva = participant("Eva");
        User carla = participant("Carla");
        for (User user : List.of(ana, eva, carla)) {
            enroll(user, daysAgo(2), inDays(28));
            seat(user, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(2), inDays(28));
        }
        complete(ana, 4);
        complete(carla, 10);
        stubRepositories();

        OrganizationHomeResponse home = service.home(organization.getId(), null, now);

        assertEquals(3, home.participants());
        assertEquals(2, home.startedParticipants());
        assertEquals(70, home.averageProgressPercentage());
        assertEquals(1, home.completedParticipants());
        assertEquals(33, home.completedPercentage());
    }

    @Test
    void shouldListActiveParticipantsMostActiveFirst() {
        User ana = participant("Ana");
        User bruno = participant("Bruno");
        for (User user : List.of(ana, bruno)) {
            enroll(user, daysAgo(10), inDays(30));
            seat(user, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(10), inDays(30));
        }
        lastActive(ana, daysAgo(1));
        lastActive(bruno, daysAgo(3));
        attempt(ana, daysAgo(1));
        attempt(bruno, daysAgo(3));
        attempt(bruno, daysAgo(4));
        attempt(bruno, daysAgo(4).plusSeconds(60));
        stubRepositories();

        OrganizationHomeResponse home = service.home(organization.getId(), null, now);

        assertEquals(2, home.activeParticipants().size());
        assertEquals(bruno.getId(), home.activeParticipants().get(0).userId());
        assertEquals(3, home.activeParticipants().get(0).activities());
        assertEquals(2, home.activeParticipants().get(0).activeDays());
        assertEquals(ana.getId(), home.activeParticipants().get(1).userId());
    }

    @Test
    void shouldFlagExpiringInactiveAndBehindPaceFirst() {
        User dario = participant("Dario");
        User fede = participant("Fede");
        User eva = participant("Eva");
        enroll(dario, daysAgo(20), inDays(3));
        seat(dario, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(20), inDays(3));
        enroll(fede, daysAgo(10), inDays(30));
        seat(fede, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(10), inDays(30));
        complete(fede, 3);
        lastActive(fede, daysAgo(9));
        enroll(eva, daysAgo(2), inDays(28));
        seat(eva, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(2), inDays(28));
        stubRepositories();

        List<FollowUpParticipant> followUp =
                service.home(organization.getId(), null, now).followUp();

        assertEquals(2, followUp.size());
        FollowUpParticipant first = followUp.get(0);
        assertEquals(dario.getId(), first.userId());
        assertEquals(
                List.of(
                        FollowUpReason.ACCESS_EXPIRING,
                        FollowUpReason.INACTIVE,
                        FollowUpReason.LOW_PROGRESS),
                first.reasons());
        assertEquals(0, first.progressPercentage());
        assertEquals(87, first.expectedProgressPercentage());
        assertEquals(inDays(3), first.accessExpiresAt());
        assertEquals(fede.getId(), followUp.get(1).userId());
        assertEquals(List.of(FollowUpReason.INACTIVE), followUp.get(1).reasons());
    }

    @Test
    void shouldNotFollowUpAnExpiredAccess() {
        User gaby = participant("Gaby");
        enroll(gaby, daysAgo(40), daysAgo(1));
        seat(gaby, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(40), daysAgo(1));
        stubRepositories();

        OrganizationHomeResponse home = service.home(organization.getId(), null, now);

        assertEquals(1, home.participants());
        assertTrue(home.followUp().isEmpty());
    }

    @Test
    void shouldHoldTheReminderUntilTheCooldownEnds() {
        User dario = participant("Dario");
        members.get(0).setLastReminderAt(now.minus(Duration.ofHours(2)));
        enroll(dario, daysAgo(20), inDays(3));
        seat(dario, PurchaseStatus.PAID, SeatStatus.ACTIVE, daysAgo(20), inDays(3));
        stubRepositories();

        FollowUpParticipant first = service.home(organization.getId(), null, now).followUp().get(0);

        assertEquals(now.plus(Duration.ofHours(22)), first.nextReminderAvailableAt());
    }

    @Test
    void shouldReturnEmptyWithoutParticipants() {
        stubRepositoriesWithoutEnrollments();

        OrganizationHomeResponse home = service.home(organization.getId(), null, now);

        assertEquals(0, home.participants());
        assertEquals(0, home.averageProgressPercentage());
        assertTrue(home.activeParticipants().isEmpty());
        assertTrue(home.followUp().isEmpty());
    }

    private void stubRepositoriesWithoutEnrollments() {
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(List.of());
        when(contentScope.resolve(eq(organization.getId()), isNull(), anyCollection(), eq(now)))
                .thenReturn(
                        new ContentScope(
                                List.of(new CourseScope(course, version)),
                                Map.of(version.getId(), version),
                                Map.of(version.getId(), 10L),
                                Map.of(),
                                Map.of(),
                                Map.of(),
                                Map.of()));
    }
}
