package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.organizations.dto.AcceptInvitationResponse;
import com.signasource.signa_api.organizations.dto.InvitationPreviewResponse;
import com.signasource.signa_api.organizations.dto.SeatResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SeatInvitationServiceTest {

    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationEnrollmentService enrollmentService;
    @Mock private EmailService emailService;

    private SeatInvitationService service;
    private Organization organization;
    private Course course;
    private CoursePurchase purchase;
    private PurchaseSeat seat;
    private User user;

    @BeforeEach
    void setUp() {
        PurchasePolicy policy = new PurchasePolicy(15, 60, 10, 5, 24, 500, 10);
        service =
                new SeatInvitationService(
                        seatRepository,
                        memberRepository,
                        userRepository,
                        enrollmentService,
                        emailService,
                        policy);
        organization =
                Organization.builder()
                        .id(UUID.randomUUID())
                        .name("Hospital")
                        .status(OrganizationStatus.ACTIVE)
                        .build();
        course = Course.builder().id(UUID.randomUUID()).name("LSA Salud").build();
        Instant paidAt = Instant.now().minus(1, ChronoUnit.DAYS);
        purchase =
                CoursePurchase.builder()
                        .id(UUID.randomUUID())
                        .organization(organization)
                        .course(course)
                        .durationDays(30)
                        .status(PurchaseStatus.PAID)
                        .paidAt(paidAt)
                        .invitationDeadline(paidAt.plus(10, ChronoUnit.DAYS))
                        .build();
        seat =
                PurchaseSeat.builder()
                        .id(UUID.randomUUID())
                        .purchase(purchase)
                        .email("ana@hospital.com")
                        .status(SeatStatus.INVITED)
                        .invitationToken("tok")
                        .invitedAt(paidAt)
                        .build();
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("Ana@Hospital.com");
        user.setVerified(false);
    }

    @Test
    void shouldIssueATokenAndMailTheInvitation() {
        seat.setStatus(SeatStatus.PENDING_PAYMENT);
        seat.setInvitationToken(null);
        Instant now = Instant.now();

        service.invite(seat, now);

        assertEquals(SeatStatus.INVITED, seat.getStatus());
        assertEquals(now, seat.getInvitedAt());
        assertNotNull(seat.getInvitationToken());
        verify(seatRepository).save(seat);
        verify(emailService)
                .sendSeatInvitationEmail(
                        eq("ana@hospital.com"),
                        eq("Hospital"),
                        eq("LSA Salud"),
                        eq(seat.getInvitationToken()),
                        eq(purchase.getInvitationDeadline()),
                        eq(30),
                        eq(false));
    }

    @Test
    void shouldReplaceTheTokenWhenInvitingAgain() {
        service.invite(seat, Instant.now());

        assertNotEquals("tok", seat.getInvitationToken());
    }

    @Test
    void shouldRetryWhenTheGeneratedTokenCollides() {
        when(seatRepository.existsByInvitationToken(anyString())).thenReturn(true, false);

        service.invite(seat, Instant.now());

        assertNotNull(seat.getInvitationToken());
    }

    @Test
    void shouldSendAManualReminderOnceTheCooldownPassed() {
        Instant now = Instant.now();

        service.remind(seat, now);

        assertEquals(now, seat.getLastReminderAt());
        verify(emailService)
                .sendSeatInvitationEmail(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyInt(),
                        eq(true));
    }

    @Test
    void shouldRejectAReminderInsideTheCooldown() {
        Instant now = Instant.now();
        seat.setLastReminderAt(now.minus(2, ChronoUnit.HOURS));

        assertThrows(InvalidInputException.class, () -> service.remind(seat, now));
        verify(emailService, never())
                .sendSeatInvitationEmail(any(), any(), any(), any(), any(), anyInt(), anyBoolean());
    }

    @Test
    void shouldRejectAReminderForAnAcceptedSeat() {
        seat.setStatus(SeatStatus.ACTIVE);

        assertThrows(InvalidInputException.class, () -> service.remind(seat, Instant.now()));
    }

    @Test
    void shouldComputeTheNextReminderFromTheLatestSend() {
        Instant reminded = seat.getInvitedAt().plus(3, ChronoUnit.HOURS);
        seat.setLastReminderAt(reminded);

        assertEquals(reminded.plus(Duration.ofHours(24)), service.nextManualReminderAt(seat));
        seat.setInvitedAt(null);
        seat.setLastReminderAt(null);
        assertNull(service.nextManualReminderAt(seat));
    }

    @Test
    void shouldSendAutomaticRemindersAndMarkThem() {
        Instant now = Instant.now();
        when(seatRepository.findDueForAutoReminder(now.minus(Duration.ofDays(5)), now))
                .thenReturn(List.of(seat));

        int sent = service.sendDueAutoReminders(now);

        assertEquals(1, sent);
        assertEquals(now, seat.getAutoReminderSentAt());
        verify(emailService)
                .sendSeatInvitationEmail(
                        anyString(),
                        anyString(),
                        anyString(),
                        anyString(),
                        any(),
                        anyInt(),
                        eq(true));
    }

    @Test
    void shouldDescribeTheInvitationForItsLink() {
        when(seatRepository.findByInvitationToken("tok")).thenReturn(Optional.of(seat));
        when(userRepository.existsByEmail("ana@hospital.com")).thenReturn(true);

        InvitationPreviewResponse preview = service.preview("tok");

        assertEquals("Hospital", preview.organizationName());
        assertEquals("LSA Salud", preview.courseName());
        assertEquals(SeatStatus.INVITED, preview.status());
        assertEquals(30, preview.durationDays());
        assertTrue(preview.accountExists());
    }

    @Test
    void shouldReturnNotFoundForAnUnknownToken() {
        when(seatRepository.findByInvitationToken("nope")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.preview("nope"));
    }

    @Test
    void shouldActivateTheSeatAndStartTheDaysOnAccept() {
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        AcceptInvitationResponse response = service.accept(user, "tok");

        assertEquals(SeatStatus.ACTIVE, seat.getStatus());
        assertEquals(user, seat.getUser());
        assertEquals(
                Duration.ofDays(30),
                Duration.between(seat.getActivatedAt(), seat.getAccessExpiresAt()));
        assertEquals(seat.getAccessExpiresAt(), response.accessExpiresAt());
        verify(enrollmentService)
                .grantCourse(user, organization, course, seat.getAccessExpiresAt());
        ArgumentCaptor<OrganizationMember> member =
                ArgumentCaptor.forClass(OrganizationMember.class);
        verify(memberRepository).save(member.capture());
        assertEquals(MemberRole.MEMBER, member.getValue().getRole());
        assertEquals(organization, member.getValue().getOrganization());
        assertTrue(user.isVerified());
        assertEquals(course, user.getCurrentCourse());
        verify(userRepository).save(user);
    }

    @Test
    void shouldKeepAnExistingMembershipOfTheSameOrganization() {
        OrganizationMember admin =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.ADMIN)
                        .status(MemberStatus.ACTIVE)
                        .build();
        user.setVerified(true);
        user.setCurrentCourse(Course.builder().id(UUID.randomUUID()).build());
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(admin));

        service.accept(user, "tok");

        assertEquals(MemberRole.ADMIN, admin.getRole());
        verify(memberRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReactivateARemovedMembership() {
        OrganizationMember removed =
                OrganizationMember.builder()
                        .user(user)
                        .organization(Organization.builder().id(UUID.randomUUID()).build())
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.REMOVED)
                        .removedAt(Instant.now())
                        .build();
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(removed));

        service.accept(user, "tok");

        assertEquals(MemberStatus.ACTIVE, removed.getStatus());
        assertEquals(organization, removed.getOrganization());
        assertNull(removed.getRemovedAt());
    }

    @Test
    void shouldRejectAUserOfAnotherOrganization() {
        OrganizationMember other =
                OrganizationMember.builder()
                        .user(user)
                        .organization(Organization.builder().id(UUID.randomUUID()).build())
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(other));

        assertThrows(ResourceAlreadyInUseException.class, () -> service.accept(user, "tok"));
        verify(enrollmentService, never()).grantCourse(any(), any(), any(), any());
    }

    @Test
    void shouldRejectAnotherEmail() {
        user.setEmail("intruso@gmail.com");
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));

        assertThrows(ForbiddenException.class, () -> service.accept(user, "tok"));
        verify(seatRepository, never()).save(any());
    }

    @Test
    void shouldRejectAnExpiredInvitation() {
        purchase.setInvitationDeadline(Instant.now().minusSeconds(1));
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));

        assertThrows(InvalidInputException.class, () -> service.accept(user, "tok"));
        verify(enrollmentService, never()).grantCourse(any(), any(), any(), any());
    }

    @Test
    void shouldRejectARevokedSeat() {
        seat.setStatus(SeatStatus.REVOKED);
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));

        assertThrows(InvalidInputException.class, () -> service.accept(user, "tok"));
    }

    @Test
    void shouldRejectWhenTheOrganizationIsNoLongerActive() {
        organization.setStatus(OrganizationStatus.SUSPENDED);
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));

        assertThrows(InvalidInputException.class, () -> service.accept(user, "tok"));
    }

    @Test
    void shouldBeIdempotentForTheUserWhoAlreadyAccepted() {
        Instant activated = Instant.now().minus(1, ChronoUnit.DAYS);
        seat.setStatus(SeatStatus.ACTIVE);
        seat.setUser(user);
        seat.setActivatedAt(activated);
        seat.setAccessExpiresAt(activated.plus(30, ChronoUnit.DAYS));
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));

        AcceptInvitationResponse response = service.accept(user, "tok");

        assertEquals(activated, response.activatedAt());
        verify(enrollmentService, never()).grantCourse(any(), any(), any(), any());
    }

    @Test
    void shouldRevokeTheActiveSeatsOfARemovedMember() {
        seat.setStatus(SeatStatus.ACTIVE);
        when(seatRepository.findByUserIdAndPurchaseOrganizationIdAndStatus(
                        user.getId(), organization.getId(), SeatStatus.ACTIVE))
                .thenReturn(List.of(seat));

        service.revokeActiveSeats(user, organization.getId());

        assertEquals(SeatStatus.REVOKED, seat.getStatus());
        assertNotNull(seat.getRevokedAt());
    }

    @Test
    void shouldOnlyOfferEditingAndRemindersWhilePending() {
        SeatResponse pending = service.toResponse(seat, Instant.now());
        seat.setStatus(SeatStatus.ACTIVE);
        seat.setUser(user);
        user.setName("Ana");
        user.setLastName("Gómez");
        SeatResponse active = service.toResponse(seat, Instant.now());

        assertTrue(pending.canEditEmail());
        assertNotNull(pending.nextReminderAvailableAt());
        assertEquals("Ana Gómez", active.userName());
        assertEquals(false, active.canEditEmail());
        assertNull(active.nextReminderAvailableAt());
    }

    @Test
    void shouldRejectAnInvitationOfARefundedPurchase() {
        purchase.setStatus(PurchaseStatus.REFUNDED);
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.of(seat));

        assertThrows(InvalidInputException.class, () -> service.accept(user, "tok"));
        verify(enrollmentService, never()).grantCourse(any(), any(), any(), any());
    }

    @Test
    void shouldReturnNotFoundForACancelledInvitationLink() {
        when(seatRepository.findForUpdateByInvitationToken("tok")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.accept(user, "tok"));
    }
}
