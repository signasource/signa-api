package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.dto.ParticipantReminderResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParticipantReminderServiceTest {

    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private EmailService emailService;
    @Mock private PurchasePolicy policy;

    @InjectMocks private ParticipantReminderService service;

    private final User actor = new User();
    private final Organization organization =
            Organization.builder().id(UUID.randomUUID()).name("Hospital").build();
    private User learner;
    private OrganizationMember member;

    @BeforeEach
    void setUp() {
        learner = new User();
        learner.setId(UUID.randomUUID());
        learner.setName("Ana");
        learner.setEmail("ana@test.com");
        member =
                OrganizationMember.builder()
                        .user(learner)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        lenient().when(policy.manualReminderCooldown()).thenReturn(Duration.ofHours(24));
        lenient()
                .when(
                        memberRepository.findByOrganizationIdAndUserId(
                                organization.getId(), learner.getId()))
                .thenReturn(Optional.of(member));
    }

    private UserCourseEnrollment enrollment(String courseName, Instant expiresAt) {
        Course course = Course.builder().id(UUID.randomUUID()).name(courseName).build();
        return UserCourseEnrollment.builder()
                .user(learner)
                .courseVersion(CourseVersion.builder().id(UUID.randomUUID()).course(course).build())
                .organization(organization)
                .status(EnrollmentStatus.ENROLLED)
                .accessExpiresAt(expiresAt)
                .build();
    }

    @Test
    void shouldEmailTheRunningCoursesAndStartTheCooldown() {
        Instant soon = Instant.now().plus(Duration.ofDays(3));
        when(enrollmentRepository.findByUserIdAndOrganizationId(
                        learner.getId(), organization.getId()))
                .thenReturn(
                        List.of(
                                enrollment("LSA Salud", soon),
                                enrollment("Refunded", Instant.now().minusSeconds(60))));

        ParticipantReminderResponse response =
                service.remind(actor, organization.getId(), learner.getId());

        assertNotNull(member.getLastReminderAt());
        assertEquals(member.getLastReminderAt(), response.lastReminderAt());
        assertEquals(
                response.lastReminderAt().plus(Duration.ofHours(24)),
                response.nextReminderAvailableAt());
        verify(memberRepository).save(member);
        verify(emailService)
                .sendParticipantReminderEmail(
                        "ana@test.com", "Ana", "Hospital", List.of("LSA Salud"), soon);
    }

    @Test
    void shouldRejectAReminderInsideTheCooldown() {
        member.setLastReminderAt(Instant.now().minus(Duration.ofHours(3)));

        assertThrows(
                InvalidInputException.class,
                () -> service.remind(actor, organization.getId(), learner.getId()));
        verify(emailService, never())
                .sendParticipantReminderEmail(anyString(), any(), any(), anyList(), any());
    }

    @Test
    void shouldRejectAReminderWithoutRunningCourses() {
        when(enrollmentRepository.findByUserIdAndOrganizationId(
                        learner.getId(), organization.getId()))
                .thenReturn(List.of(enrollment("Ended", Instant.now().minusSeconds(60))));

        assertThrows(
                InvalidInputException.class,
                () -> service.remind(actor, organization.getId(), learner.getId()));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldNotRemindSomeoneWhoLeft() {
        member.setStatus(MemberStatus.REMOVED);

        assertThrows(
                NotFoundException.class,
                () -> service.remind(actor, organization.getId(), learner.getId()));
    }

    @Test
    void shouldRequireManagingTheOrganization() {
        doThrow(new ForbiddenException("nope"))
                .when(accessService)
                .requireManage(actor, organization.getId());

        assertThrows(
                ForbiddenException.class,
                () -> service.remind(actor, organization.getId(), learner.getId()));
        verify(memberRepository, never())
                .findByOrganizationIdAndUserId(eq(organization.getId()), any());
    }
}
