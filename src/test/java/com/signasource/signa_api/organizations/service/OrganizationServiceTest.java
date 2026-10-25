package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.dto.CourseSummaryResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.organizations.dto.CreateOrganizationRequest;
import com.signasource.signa_api.organizations.dto.MyOrganizationResponse;
import com.signasource.signa_api.organizations.dto.OrganizationAdminResponse;
import com.signasource.signa_api.organizations.dto.OrganizationCourseResponse;
import com.signasource.signa_api.organizations.dto.OrganizationDetailResponse;
import com.signasource.signa_api.organizations.dto.OrganizationResponse;
import com.signasource.signa_api.organizations.dto.RegisterOrganizationRequest;
import com.signasource.signa_api.organizations.dto.RegisterOrganizationResponse;
import com.signasource.signa_api.organizations.dto.UpdateOrganizationRequest;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
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
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class OrganizationServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private OrganizationAccessService accessService;

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
        ArgumentCaptor<Organization> saved = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(saved.capture());
        assertEquals(OrganizationStatus.ACTIVE, saved.getValue().getStatus());
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
        assertEquals(OrganizationStatus.PENDING, response.status());
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

    @Test
    void shouldRegisterOrganizationSuccessfully() {
        when(organizationRepository.existsByNameIgnoreCase("Empresa")).thenReturn(false);
        when(organizationRepository.existsByCuit("30123456789")).thenReturn(false);
        when(userRepository.existsByEmail("admin@empresa.com")).thenReturn(false);
        when(userRepository.existsByUsername(any())).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userSettingsRepository.save(any(UserSettings.class)))
                .thenAnswer(inv -> inv.getArgument(0));
        when(userStatsRepository.save(any(UserStats.class))).thenAnswer(inv -> inv.getArgument(0));
        Organization saved = Organization.builder().id(UUID.randomUUID()).name("Empresa").build();
        when(organizationRepository.save(any(Organization.class))).thenReturn(saved);
        when(memberRepository.save(any(OrganizationMember.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        RegisterOrganizationResponse response =
                organizationService.registerOrganization(
                        new RegisterOrganizationRequest(
                                "Empresa",
                                "30-12345678-9",
                                " Admin@Empresa.com ",
                                null,
                                "Admin User",
                                "password123"));

        assertEquals("Empresa", response.organizationName());
        assertNotNull(response.status());
        ArgumentCaptor<User> admin = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(admin.capture());
        assertEquals("admin@empresa.com", admin.getValue().getEmail());
        assertEquals(Role.ORG_ADMIN, admin.getValue().getRole());
        ArgumentCaptor<Organization> org = ArgumentCaptor.forClass(Organization.class);
        verify(organizationRepository).save(org.capture());
        assertEquals("30123456789", org.getValue().getCuit());
        assertEquals(OrganizationStatus.PENDING, org.getValue().getStatus());
        verify(userSettingsRepository).save(any(UserSettings.class));
        verify(userStatsRepository).save(any(UserStats.class));
        verify(memberRepository).save(any(OrganizationMember.class));
    }

    @Test
    void shouldRejectRegistrationWithMalformedCuit() {
        assertThrows(
                InvalidInputException.class,
                () ->
                        organizationService.registerOrganization(
                                new RegisterOrganizationRequest(
                                        "Empresa",
                                        "30-123-9",
                                        "admin@empresa.com",
                                        null,
                                        "Admin User",
                                        "password123")));
        verify(organizationRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldReturnOrganizationDetailAfterCheckingAccess() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(memberRepository.countByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(3L);

        OrganizationDetailResponse detail =
                organizationService.getOrganization(user, organization.getId());

        verify(accessService).requireManage(user, organization.getId());
        assertEquals("Hospital", detail.name());
        assertEquals(3, detail.activeMembers());
    }

    @Test
    void shouldRenameOrganization() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.existsByNameIgnoreCase("Hospital Central")).thenReturn(false);
        when(memberRepository.countByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(4L);
        when(organizationRepository.save(organization)).thenReturn(organization);

        OrganizationDetailResponse detail =
                organizationService.updateOrganization(
                        organization.getId(), new UpdateOrganizationRequest("Hospital Central"));

        assertEquals("Hospital Central", detail.name());
    }

    @Test
    void shouldLeaveFieldsUntouchedWhenUpdateIsEmptyOrNameOnlyChangesCase() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.save(organization)).thenReturn(organization);

        OrganizationDetailResponse detail =
                organizationService.updateOrganization(
                        organization.getId(), new UpdateOrganizationRequest("HOSPITAL"));

        assertEquals("Hospital", detail.name());
        assertEquals(OrganizationStatus.PENDING, detail.status());
        verify(organizationRepository, never()).existsByNameIgnoreCase(any());
    }

    @Test
    void shouldRejectRenamingToAnExistingOrganizationName() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(organizationRepository.existsByNameIgnoreCase("Banco")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        organizationService.updateOrganization(
                                organization.getId(), new UpdateOrganizationRequest("Banco")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingUnknownOrganization() {
        UUID unknown = UUID.randomUUID();
        when(organizationRepository.findById(unknown)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        organizationService.updateOrganization(
                                unknown, new UpdateOrganizationRequest(null)));
    }

    @Test
    void shouldListActiveAdminsAfterCheckingAccess() {
        OrganizationMember admin =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.ADMIN)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        organization.getId(), MemberRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(List.of(admin));

        List<OrganizationAdminResponse> admins =
                organizationService.getAdmins(user, organization.getId());

        verify(accessService).requireManage(user, organization.getId());
        assertEquals(1, admins.size());
        assertEquals("boss@hospital.com", admins.get(0).email());
    }

    @Test
    void shouldThrowWhenOrgNameAlreadyExists() {
        when(organizationRepository.existsByNameIgnoreCase("Empresa")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        organizationService.registerOrganization(
                                new RegisterOrganizationRequest(
                                        "Empresa",
                                        "30-12345678-9",
                                        "admin@empresa.com",
                                        null,
                                        "Admin User",
                                        "password123")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCuitAlreadyExists() {
        when(organizationRepository.existsByNameIgnoreCase("Empresa")).thenReturn(false);
        when(organizationRepository.existsByCuit("30123456789")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        organizationService.registerOrganization(
                                new RegisterOrganizationRequest(
                                        "Empresa",
                                        "30-12345678-9",
                                        "admin@empresa.com",
                                        null,
                                        "Admin User",
                                        "password123")));
        verify(organizationRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEmailAlreadyInUse() {
        when(organizationRepository.existsByNameIgnoreCase("Empresa")).thenReturn(false);
        when(organizationRepository.existsByCuit("30123456789")).thenReturn(false);
        when(userRepository.existsByEmail("admin@empresa.com")).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        organizationService.registerOrganization(
                                new RegisterOrganizationRequest(
                                        "Empresa",
                                        "30-12345678-9",
                                        "admin@empresa.com",
                                        null,
                                        "Admin User",
                                        "password123")));
        verify(userRepository, never()).save(any());
    }
}
