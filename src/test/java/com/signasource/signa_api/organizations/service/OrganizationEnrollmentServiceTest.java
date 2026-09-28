package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationEnrollmentServiceTest {

    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private OrganizationEnrollmentService enrollmentService;

    private Organization organization;
    private Course course;
    private CourseVersion version;
    private User user;

    @BeforeEach
    void setUp() {
        organization = Organization.builder().id(UUID.randomUUID()).name("Hospital").build();
        course = Course.builder().id(UUID.randomUUID()).name("LSA para Salud").build();
        version = CourseVersion.builder().id(UUID.randomUUID()).course(course).build();
        user = new User();
        user.setId(UUID.randomUUID());
    }

    private void stubContractedCourse() {
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organization.getId()))
                .thenReturn(
                        List.of(
                                OrganizationCourse.builder()
                                        .organization(organization)
                                        .course(course)
                                        .build()));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
    }

    @Test
    void shouldCreateTaggedEnrollmentsForEveryContractedCourse() {
        stubContractedCourse();
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.empty());
        Instant expiry = Instant.now().plusSeconds(3600);

        List<Course> granted = enrollmentService.grantContractedCourses(user, organization, expiry);

        assertEquals(List.of(course), granted);
        ArgumentCaptor<UserCourseEnrollment> captor =
                ArgumentCaptor.forClass(UserCourseEnrollment.class);
        verify(enrollmentRepository).save(captor.capture());
        UserCourseEnrollment saved = captor.getValue();
        assertEquals(EnrollmentStatus.ENROLLED, saved.getStatus());
        assertEquals(organization, saved.getOrganization());
        assertEquals(expiry, saved.getAccessExpiresAt());
        assertEquals(user, saved.getUser());
        assertEquals(version, saved.getCourseVersion());
    }

    @Test
    void shouldTagAnExistingSelfEnrollmentKeepingItsProgress() {
        stubContractedCourse();
        UserCourseEnrollment existing =
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.COMPLETED)
                        .build();
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.of(existing));

        enrollmentService.grantContractedCourses(user, organization, null);

        assertEquals(EnrollmentStatus.COMPLETED, existing.getStatus());
        assertEquals(organization, existing.getOrganization());
        verify(enrollmentRepository).save(existing);
    }

    @Test
    void shouldReactivateADroppedEnrollment() {
        stubContractedCourse();
        UserCourseEnrollment dropped =
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.DROPPED)
                        .organization(organization)
                        .build();
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.of(dropped));

        enrollmentService.grantContractedCourses(user, organization, null);

        assertEquals(EnrollmentStatus.ENROLLED, dropped.getStatus());
        assertNull(dropped.getAccessExpiresAt());
    }

    @Test
    void shouldFailWhenAContractedCourseHasNoPublishedVersion() {
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organization.getId()))
                .thenReturn(
                        List.of(
                                OrganizationCourse.builder()
                                        .organization(organization)
                                        .course(course)
                                        .build()));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> enrollmentService.grantContractedCourses(user, organization, null));
    }

    @Test
    void shouldGrantNewCourseOnlyToActiveMembersNotAdmins() {
        OrganizationMember member =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(List.of(member));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.empty());

        enrollmentService.grantCourseToActiveMembers(organization, course);

        verify(enrollmentRepository, times(1)).save(any(UserCourseEnrollment.class));
    }

    @Test
    void shouldDropEnrollmentsWhenTheCourseIsUncontracted() {
        UserCourseEnrollment enrollment =
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .build();
        when(enrollmentRepository.findByOrganizationIdAndCourseVersionCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(List.of(enrollment));

        enrollmentService.revokeCourseFromMembers(organization.getId(), course.getId());

        assertEquals(EnrollmentStatus.DROPPED, enrollment.getStatus());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void shouldDropAllEnrollmentsAndClearCurrentCourseWhenItWasRevoked() {
        UserCourseEnrollment enrollment =
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .build();
        user.setCurrentCourse(course);
        when(enrollmentRepository.findByUserIdAndOrganizationId(user.getId(), organization.getId()))
                .thenReturn(List.of(enrollment));

        enrollmentService.revokeAllFromUser(user, organization.getId());

        assertEquals(EnrollmentStatus.DROPPED, enrollment.getStatus());
        assertNull(user.getCurrentCourse());
        verify(userRepository).save(user);
    }

    @Test
    void shouldKeepCurrentCourseWhenItWasNotRevoked() {
        UserCourseEnrollment enrollment =
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .build();
        Course other = Course.builder().id(UUID.randomUUID()).name("Own course").build();
        user.setCurrentCourse(other);
        when(enrollmentRepository.findByUserIdAndOrganizationId(user.getId(), organization.getId()))
                .thenReturn(List.of(enrollment));

        enrollmentService.revokeAllFromUser(user, organization.getId());

        assertEquals(other, user.getCurrentCourse());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotTouchUserWhenThereIsNoCurrentCourse() {
        when(enrollmentRepository.findByUserIdAndOrganizationId(user.getId(), organization.getId()))
                .thenReturn(List.of());

        enrollmentService.revokeAllFromUser(user, organization.getId());

        verify(userRepository, never()).save(any());
    }
}
