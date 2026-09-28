package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.repository.InviteCodeRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InviteCodeServiceTest {

    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseTrackingService courseTrackingService;

    @InjectMocks private InviteCodeService inviteCodeService;

    private User user;
    private Organization organization;
    private Course course;
    private CourseVersion courseVersion;
    private InviteCode inviteCode;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());

        organization = Organization.builder().id(UUID.randomUUID()).name("Hospital San Martín").build();
        course =
                Course.builder()
                        .id(UUID.randomUUID())
                        .name("LSA para Salud")
                        .signLanguage(SignLanguage.builder().code("LSA").build())
                        .build();
        courseVersion = CourseVersion.builder().id(UUID.randomUUID()).course(course).build();

        inviteCode =
                InviteCode.builder()
                        .id(UUID.randomUUID())
                        .code("HSMT2026")
                        .organization(organization)
                        .course(course)
                        .active(true)
                        .useCount(0)
                        .build();
    }

    @Test
    void createInviteCode_GeneratesAndSavesACode() {
        UUID organizationId = organization.getId();
        CreateInviteCodeRequest request = new CreateInviteCodeRequest(course.getId(), null, null);
        when(organizationRepository.findById(organizationId)).thenReturn(Optional.of(organization));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(inviteCodeRepository.existsByCode(any())).thenReturn(false);
        when(inviteCodeRepository.save(any(InviteCode.class))).thenAnswer(i -> i.getArgument(0));

        InviteCodeResponse response = inviteCodeService.createInviteCode(organizationId, request);

        assertEquals(8, response.code().length());
        assertEquals(organizationId, response.organizationId());
        assertEquals(course.getId(), response.courseId());
    }

    @Test
    void redeem_EnrollsAndReturnsCourse_WhenCodeIsValidAndUnused() {
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(courseVersionRepository.findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(courseVersion));
        when(enrollmentRepository.existsByUserIdAndCourseVersionId(user.getId(), courseVersion.getId()))
                .thenReturn(false);

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "hsmt2026");

        assertFalse(response.alreadyEnrolled());
        assertEquals("Hospital San Martín", response.organizationName());
        assertEquals(course.getId(), response.course().id());
        verify(courseTrackingService)
                .enrollUserInCourse(user, courseVersion.getId(), organization, inviteCode.getExpiresAt());
        assertEquals(1, inviteCode.getUseCount());
        verify(inviteCodeRepository).save(inviteCode);
    }

    @Test
    void redeem_SkipsEnrollment_WhenAlreadyEnrolled() {
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(courseVersionRepository.findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(courseVersion));
        when(enrollmentRepository.existsByUserIdAndCourseVersionId(user.getId(), courseVersion.getId()))
                .thenReturn(true);

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertTrue(response.alreadyEnrolled());
        verify(courseTrackingService, never()).enrollUserInCourse(any(), any(), any(), any());
        verify(inviteCodeRepository, never()).save(any());
    }

    @Test
    void redeem_ThrowsInvalidInput_WhenCodeDoesNotExist() {
        when(inviteCodeRepository.findByCode("NOPE0000")).thenReturn(Optional.empty());

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "nope0000"));
    }

    @Test
    void redeem_ThrowsInvalidInput_WhenCodeExpired() {
        inviteCode.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
        verify(courseTrackingService, never()).enrollUserInCourse(any(), any(), any(), any());
    }

    @Test
    void redeem_ThrowsInvalidInput_WhenCodeReachedMaxUses() {
        inviteCode.setMaxUses(1);
        inviteCode.setUseCount(1);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
    }

    @Test
    void redeem_ThrowsNotFound_WhenCoursesHasNoPublishedVersion() {
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(courseVersionRepository.findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
    }
}
