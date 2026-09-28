package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.MyOrganizationResponse;
import com.signasource.signa_api.organizations.dto.OrganizationCourseResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.Role;
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
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationEnrollmentService enrollmentService;

    @InjectMocks private OrganizationService organizationService;

    private Organization organization;
    private Course course;
    private User user;

    @BeforeEach
    void setUp() {
        organization = Organization.builder().id(UUID.randomUUID()).name("Hospital").build();
        course =
                Course.builder()
                        .id(UUID.randomUUID())
                        .name("LSA para Salud")
                        .signLanguage(SignLanguage.builder().code("LSA").build())
                        .build();
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("boss@hospital.com");
        user.setEnabled(true);
        user.setRole(Role.USER);
    }

    @Test
    void shouldCreateOrganization() {
        when(organizationRepository.existsByNameIgnoreCase("Hospital")).thenReturn(false);
        when(organizationRepository.save(any(Organization.class))).thenReturn(organization);

        OrganizationResponse response =
                organizationService.createOrganization(new CreateOrganizationRequest("Hospital"));

        assertEquals(organization.getId(), response.id());
        assertEquals("Hospital", response.name());
    }

    @Test
    void shouldRejectDuplicateOrganizationName() {
        when(organizationRepository.existsByNameIgnoreCase("Hospital")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        organizationService.createOrganization(
                                new CreateOrganizationRequest("Hospital")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void shouldPromoteUserToOrgAdminWhenAddingAdmin() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(userRepository.findByEmail("boss@hospital.com")).thenReturn(Optional.of(user));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        organizationService.addAdmin(organization.getId(), "boss@hospital.com");

        ArgumentCaptor<OrganizationMember> captor =
                ArgumentCaptor.forClass(OrganizationMember.class);
        verify(memberRepository).save(captor.capture());
        assertEquals(MemberRole.ADMIN, captor.getValue().getRole());
        assertEquals(MemberStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(organization, captor.getValue().getOrganization());
        assertEquals(Role.ORG_ADMIN, user.getRole());
        verify(userRepository).save(user);
    }

    @Test
    void shouldPromoteExistingMemberOfTheSameOrganization() {
        user.setRole(Role.ADMIN);
        OrganizationMember existing =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.REMOVED)
                        .removedAt(Instant.now())
                        .build();
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(userRepository.findByEmail("boss@hospital.com")).thenReturn(Optional.of(user));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        organizationService.addAdmin(organization.getId(), "boss@hospital.com");

        assertEquals(MemberRole.ADMIN, existing.getRole());
        assertEquals(MemberStatus.ACTIVE, existing.getStatus());
        assertEquals(Role.ADMIN, user.getRole());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldRejectAdminWhoBelongsToAnotherOrganization() {
        Organization other = Organization.builder().id(UUID.randomUUID()).name("Other").build();
        OrganizationMember existing =
                OrganizationMember.builder()
                        .user(user)
                        .organization(other)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(userRepository.findByEmail("boss@hospital.com")).thenReturn(Optional.of(user));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(existing));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> organizationService.addAdmin(organization.getId(), "boss@hospital.com"));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenAdminEmailIsUnknownOrDisabled() {
        user.setEnabled(false);
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(userRepository.findByEmail("boss@hospital.com")).thenReturn(Optional.of(user));

        assertThrows(
                NotFoundException.class,
                () -> organizationService.addAdmin(organization.getId(), "boss@hospital.com"));
    }

    @Test
    void shouldThrowNotFoundWhenAddingAdminToUnknownOrganization() {
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> organizationService.addAdmin(organization.getId(), "boss@hospital.com"));
    }

    @Test
    void shouldContractCourseAndGrantItToActiveMembers() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(organizationCourseRepository.existsByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(false);
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(new CourseVersion()));
        when(organizationCourseRepository.save(any(OrganizationCourse.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        OrganizationCourseResponse response =
                organizationService.addCourse(organization.getId(), course.getId());

        assertEquals(course.getId(), response.course().id());
        verify(enrollmentService).grantCourseToActiveMembers(organization, course);
    }

    @Test
    void shouldRejectCourseAlreadyContracted() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(organizationCourseRepository.existsByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> organizationService.addCourse(organization.getId(), course.getId()));
        verify(organizationCourseRepository, never()).save(any());
    }

    @Test
    void shouldRejectCourseWithoutPublishedVersion() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> organizationService.addCourse(organization.getId(), course.getId()));
        verify(organizationCourseRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenContractingUnknownCourse() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(courseRepository.findById(course.getId())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> organizationService.addCourse(organization.getId(), course.getId()));
    }

    @Test
    void shouldRemoveContractedCourseAndRevokeMemberAccess() {
        OrganizationCourse contracted =
                OrganizationCourse.builder().organization(organization).course(course).build();
        when(organizationCourseRepository.findByOrganizationIdAndCourseId(
                        organization.getId(), course.getId()))
                .thenReturn(Optional.of(contracted));

        organizationService.removeCourse(organization.getId(), course.getId());

        verify(organizationCourseRepository).delete(contracted);
        verify(enrollmentService).revokeCourseFromMembers(organization.getId(), course.getId());
    }

    @Test
    void shouldThrowNotFoundWhenRemovingCourseThatWasNeverContracted() {
        when(organizationCourseRepository.findByOrganizationIdAndCourseId(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> organizationService.removeCourse(organization.getId(), course.getId()));
        verify(enrollmentService, never()).revokeCourseFromMembers(any(), any());
    }

    @Test
    void shouldListContractedCoursesAfterCheckingAccess() {
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organization.getId()))
                .thenReturn(
                        List.of(
                                OrganizationCourse.builder()
                                        .organization(organization)
                                        .course(course)
                                        .build()));

        List<OrganizationCourseResponse> result =
                organizationService.getCourses(user, organization.getId());

        verify(accessService).requireManage(user, organization.getId());
        assertEquals(1, result.size());
        assertEquals("LSA para Salud", result.get(0).course().name());
    }

    @Test
    void shouldReturnTheUsersOrganizationWithItsCourses() {
        OrganizationMember member =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .joinedAt(Instant.parse("2026-09-01T00:00:00Z"))
                        .build();
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organization.getId()))
                .thenReturn(
                        List.of(
                                OrganizationCourse.builder()
                                        .organization(organization)
                                        .course(course)
                                        .build()));

        MyOrganizationResponse response = organizationService.getMyOrganization(user);

        assertEquals("Hospital", response.name());
        assertEquals(MemberRole.MEMBER, response.role());
        assertEquals(Instant.parse("2026-09-01T00:00:00Z"), response.joinedAt());
        assertEquals(List.of(CourseSummaryResponse.from(course)), response.courses());
    }

    @Test
    void shouldThrowNotFoundWhenUserHasNoActiveMembership() {
        OrganizationMember removed =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.REMOVED)
                        .build();
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(removed));

        assertThrows(NotFoundException.class, () -> organizationService.getMyOrganization(user));
    }

    @Test
    void shouldThrowNotFoundWhenUserHasNoMembershipAtAll() {
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> organizationService.getMyOrganization(user));
    }
}
