package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminOrganizationResponse;
import com.signasource.signa_api.admin.dto.AdminSignReportGroupResponse;
import com.signasource.signa_api.admin.dto.AdminStatsResponse;
import com.signasource.signa_api.admin.dto.AdminUserResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.dto.CreateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.admin.dto.UpdateSignReportGroupRequest;
import com.signasource.signa_api.admin.dto.UpdateUserRequest;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.service.OrganizationEnrollmentService;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private OrganizationEnrollmentService enrollmentService;
    @Mock private SignReportRepository signReportRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SignRepository signRepository;
    @Mock private PasswordEncoder passwordEncoder;

    @InjectMocks private AdminService adminService;

    private UUID orgId;
    private Organization org;
    private User adminUser;

    @BeforeEach
    void setUp() {
        orgId = UUID.randomUUID();
        org =
                Organization.builder()
                        .id(orgId)
                        .name("Test Org")
                        .status(OrganizationStatus.PENDING)
                        .cuit("30-12345678-9")
                        .contactEmail("test@test.com")
                        .memberLimit(50)
                        .createdAt(Instant.now())
                        .build();

        adminUser =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("admin@signa.com")
                        .username("admin")
                        .name("Admin")
                        .lastName("Signa")
                        .role(Role.ADMIN)
                        .enabled(true)
                        .verified(true)
                        .providers(new HashSet<>(Set.of(AuthProvider.LOCAL)))
                        .build();
    }

    @Test
    void shouldReturnStats() {
        when(organizationRepository.countByStatus(OrganizationStatus.ACTIVE)).thenReturn(5L);
        when(organizationRepository.countByStatus(OrganizationStatus.PENDING)).thenReturn(2L);
        when(signReportRepository.countByStatus(ReportStatus.PENDING)).thenReturn(3L);
        when(signReportRepository.countByStatus(ReportStatus.REVIEWED)).thenReturn(1L);
        when(userRepository.countByEnabled(true)).thenReturn(100L);
        when(waitlistRepository.count()).thenReturn(20L);

        AdminStatsResponse stats = adminService.getStats();

        assertEquals(5, stats.activeOrganizations());
        assertEquals(2, stats.pendingOrganizations());
        assertEquals(4, stats.openSignReports());
        assertEquals(100, stats.totalUsers());
        assertEquals(20, stats.waitlistCount());
    }

    @Test
    void shouldGetOrganization() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        orgId, MemberRole.ADMIN, MemberStatus.ACTIVE))
                .thenReturn(List.of());
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(
                        orgId, MemberRole.MEMBER, MemberStatus.ACTIVE))
                .thenReturn(List.of());

        AdminOrganizationResponse result = adminService.getOrganization(orgId);

        assertEquals(orgId, result.id());
        assertEquals("Test Org", result.name());
        assertEquals(OrganizationStatus.PENDING, result.status());
    }

    @Test
    void shouldThrowNotFoundWhenOrgDoesNotExist() {
        when(organizationRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> adminService.getOrganization(UUID.randomUUID()));
    }

    @Test
    void shouldApproveOrganization() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        UpdateOrganizationStatusRequest req =
                new UpdateOrganizationStatusRequest(OrganizationStatus.ACTIVE, List.of());
        adminService.updateOrganizationStatus(orgId, req);

        verify(organizationRepository).save(any());
    }

    @Test
    void shouldRejectOrganization() {
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        UpdateOrganizationStatusRequest req =
                new UpdateOrganizationStatusRequest(OrganizationStatus.REJECTED, null);
        adminService.updateOrganizationStatus(orgId, req);

        verify(organizationRepository).save(any());
    }

    @Test
    void shouldThrowOnInvalidStatusTransition() {
        org =
                Organization.builder()
                        .id(orgId)
                        .name("Active Org")
                        .status(OrganizationStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .build();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));

        UpdateOrganizationStatusRequest req =
                new UpdateOrganizationStatusRequest(OrganizationStatus.ACTIVE, null);

        assertThrows(
                InvalidInputException.class,
                () -> adminService.updateOrganizationStatus(orgId, req));
    }

    @Test
    void shouldCreateAdmin() {
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(adminUser);
        when(userSettingsRepository.save(any())).thenReturn(UserSettings.builder().build());

        CreateAdminRequest req =
                new CreateAdminRequest("new@signa.com", "Nuevo", "Admin", "password123");
        AdminUserResponse result = adminService.createAdmin(req);

        assertNotNull(result);
        verify(userRepository).save(any());
        verify(userSettingsRepository).save(any());
    }

    @Test
    void shouldThrowWhenCreatingAdminWithExistingEmail() {
        when(userRepository.existsByEmail("existing@signa.com")).thenReturn(true);

        CreateAdminRequest req =
                new CreateAdminRequest("existing@signa.com", "Nombre", "Apellido", "pass");

        assertThrows(ResourceAlreadyInUseException.class, () -> adminService.createAdmin(req));
    }

    @Test
    void shouldReturnAdminList() {
        when(userRepository.findByRole(Role.ADMIN)).thenReturn(List.of(adminUser));

        List<AdminUserResponse> admins = adminService.getAdmins();

        assertEquals(1, admins.size());
        assertEquals("admin@signa.com", admins.get(0).email());
    }

    @Test
    void shouldGetOrganizations() {
        when(organizationRepository.findByStatusIn(any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(org)));
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        Page<AdminOrganizationResponse> result =
                adminService.getOrganizations(null, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldSuspendOrganization() {
        org =
                Organization.builder()
                        .id(orgId)
                        .name("Active Org")
                        .status(OrganizationStatus.ACTIVE)
                        .createdAt(Instant.now())
                        .build();
        when(organizationRepository.findById(orgId)).thenReturn(Optional.of(org));
        when(organizationRepository.save(any())).thenReturn(org);
        when(memberRepository.findByOrganizationIdAndRoleAndStatus(any(), any(), any()))
                .thenReturn(List.of());

        UpdateOrganizationStatusRequest req =
                new UpdateOrganizationStatusRequest(OrganizationStatus.SUSPENDED, null);
        adminService.updateOrganizationStatus(orgId, req);

        verify(organizationRepository).save(any());
    }

    @Test
    void shouldGetSignReportGroups() {
        SignLanguage lang =
                SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();
        Sign sign =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("hola")
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(lang)
                        .build();

        SignReport report = new SignReport();
        report.setId(UUID.randomUUID());
        report.setSign(sign);
        report.setUser(adminUser);
        report.setStatus(ReportStatus.PENDING);
        report.setReason(ReportReason.INCORRECT_SIGN);
        report.setCreatedAt(LocalDateTime.now());

        when(signReportRepository.findByStatusIn(any())).thenReturn(List.of(report));

        List<AdminSignReportGroupResponse> groups = adminService.getSignReportGroups(null);

        assertEquals(1, groups.size());
        assertEquals("hola", groups.get(0).signMeaning());
        assertEquals(ReportStatus.PENDING, groups.get(0).status());
    }

    @Test
    void shouldUpdateSignReportGroup() {
        UUID signId = UUID.randomUUID();
        SignLanguage lang =
                SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();
        Sign sign =
                Sign.builder()
                        .id(signId)
                        .meaning("gracias")
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(lang)
                        .build();

        SignReport report = new SignReport();
        report.setId(UUID.randomUUID());
        report.setSign(sign);
        report.setUser(adminUser);
        report.setStatus(ReportStatus.PENDING);
        report.setReason(ReportReason.OTHER);
        report.setCreatedAt(LocalDateTime.now());

        when(signReportRepository.findBySignId(signId)).thenReturn(List.of(report));
        when(signReportRepository.saveAll(any())).thenReturn(List.of(report));

        UpdateSignReportGroupRequest req =
                new UpdateSignReportGroupRequest(ReportStatus.RESOLVED, "Verified OK");
        AdminSignReportGroupResponse result =
                adminService.updateSignReportGroup(signId, req, adminUser);

        assertEquals(signId, result.signId());
        verify(signReportRepository).saveAll(any());
    }

    @Test
    void shouldThrowNotFoundWhenNoReportsForSign() {
        when(signReportRepository.findBySignId(any())).thenReturn(List.of());

        assertThrows(
                NotFoundException.class,
                () ->
                        adminService.updateSignReportGroup(
                                UUID.randomUUID(),
                                new UpdateSignReportGroupRequest(ReportStatus.RESOLVED, null),
                                adminUser));
    }

    @Test
    void shouldGetUsers() {
        when(userRepository.adminSearch(any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(adminUser)));
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        Page<AdminUserResponse> result = adminService.getUsers(null, null, Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
    }

    @Test
    void shouldGetUser() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(memberRepository.findByUserId(adminUser.getId())).thenReturn(Optional.empty());

        AdminUserResponse result = adminService.getUser(adminUser.getId());

        assertEquals(adminUser.getEmail(), result.email());
    }

    @Test
    void shouldThrowNotFoundWhenUserNotFound() {
        when(userRepository.findById(any())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminService.getUser(UUID.randomUUID()));
    }

    @Test
    void shouldUpdateUser() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(userRepository.save(any())).thenReturn(adminUser);
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        UpdateUserRequest req = new UpdateUserRequest(Role.ADMIN, true);
        AdminUserResponse result = adminService.updateUser(adminUser.getId(), req);

        assertNotNull(result);
        verify(userRepository).save(any());
    }

    @Test
    void shouldGetWaitlist() {
        WaitlistEntry entry =
                WaitlistEntry.builder()
                        .id(1L)
                        .email("user@test.com")
                        .createdAt(Instant.now())
                        .build();
        when(waitlistRepository.findAllByOrderByCreatedAtDesc(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entry)));

        Page<AdminWaitlistEntryResponse> result = adminService.getWaitlist(Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        assertEquals("user@test.com", result.getContent().get(0).email());
    }

    @Test
    void shouldGetCourses() {
        when(courseRepository.findAll()).thenReturn(List.of());

        List<com.signasource.signa_api.admin.dto.AdminCourseResponse> result =
                adminService.getCourses();

        assertNotNull(result);
    }

    @Test
    void shouldGetSigns() {
        SignLanguage lang =
                SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();
        Sign sign =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning("hola")
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(lang)
                        .build();

        when(signRepository.findByMeaningContainingIgnoreCase(anyString(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(sign)));
        when(signReportRepository.countBySignIdAndStatusIn(any(), any())).thenReturn(0L);

        Page<com.signasource.signa_api.admin.dto.AdminSignResponse> result =
                adminService.getSigns("hola", Pageable.unpaged());

        assertEquals(1, result.getTotalElements());
        assertEquals("hola", result.getContent().get(0).meaning());
    }

    @Test
    void shouldGetSignsWithoutQuery() {
        when(signRepository.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        Page<com.signasource.signa_api.admin.dto.AdminSignResponse> result =
                adminService.getSigns(null, Pageable.unpaged());

        assertNotNull(result);
    }
}
