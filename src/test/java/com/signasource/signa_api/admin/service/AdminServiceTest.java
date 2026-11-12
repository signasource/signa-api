package com.signasource.signa_api.admin.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.admin.dto.AdminOrganizationResponse;
import com.signasource.signa_api.admin.dto.AdminSignReportResponse;
import com.signasource.signa_api.admin.dto.AdminStatsResponse;
import com.signasource.signa_api.admin.dto.AdminUserResponse;
import com.signasource.signa_api.admin.dto.AdminWaitlistEntryResponse;
import com.signasource.signa_api.admin.dto.CreateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateAdminRequest;
import com.signasource.signa_api.admin.dto.UpdateOrganizationStatusRequest;
import com.signasource.signa_api.admin.dto.UpdateSignReportRequest;
import com.signasource.signa_api.admin.dto.UpdateUserRequest;
import com.signasource.signa_api.auth.entity.TokenType;
import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.notification.service.NotificationService;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.AuthProvider;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserSettings;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import com.signasource.signa_api.users.service.UserService;
import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private SignReportRepository signReportRepository;
    @Mock private UserRepository userRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private WaitlistRepository waitlistRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private SignRepository signRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private UserService userService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private NotificationService notificationService;

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
                new UpdateOrganizationStatusRequest(OrganizationStatus.ACTIVE);
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
                new UpdateOrganizationStatusRequest(OrganizationStatus.REJECTED);
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
                new UpdateOrganizationStatusRequest(OrganizationStatus.ACTIVE);

        assertThrows(
                InvalidInputException.class,
                () -> adminService.updateOrganizationStatus(orgId, req));
    }

    @Test
    void shouldCreateAdmin() {
        when(userRepository.findFirstByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenReturn(adminUser);
        when(userSettingsRepository.save(any())).thenReturn(UserSettings.builder().build());

        CreateAdminRequest req =
                new CreateAdminRequest("new@signa.com", "Nuevo", "Admin", "password123", null);
        AdminUserResponse result = adminService.createAdmin(req);

        assertNotNull(result);
        verify(userRepository).save(any());
        verify(userSettingsRepository).save(any());
    }

    @Test
    void shouldUpdateAdminProfileAndRevokeSessionsOnPasswordChange() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(userRepository.findFirstByEmailIgnoreCase("nuevo@signa.com"))
                .thenReturn(Optional.empty());
        when(passwordEncoder.encode("nuevaClave1")).thenReturn("new-hash");
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        adminService.updateAdmin(
                adminUser.getId(),
                new UpdateAdminRequest(
                        "Ana",
                        "Perez",
                        "nuevo@signa.com",
                        "nuevaClave1",
                        "data:image/png;base64,AAAA"));

        assertEquals("Ana", adminUser.getName());
        assertEquals("Perez", adminUser.getLastName());
        assertEquals("nuevo@signa.com", adminUser.getEmail());
        assertEquals("new-hash", adminUser.getPasswordHash());
        assertEquals("data:image/png;base64,AAAA", adminUser.getAvatar());
        verify(tokenRepository).deleteByUserAndType(adminUser, TokenType.REFRESH);
    }

    @Test
    void shouldLeaveUntouchedWhatTheUpdateOmitsAndRemoveAvatarWithEmptyString() {
        adminUser.setAvatar("data:image/png;base64,AAAA");
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        adminService.updateAdmin(
                adminUser.getId(), new UpdateAdminRequest(null, null, null, null, ""));

        assertEquals("Admin", adminUser.getName());
        assertEquals("admin@signa.com", adminUser.getEmail());
        assertEquals(null, adminUser.getAvatar());
        verify(passwordEncoder, never()).encode(anyString());
        verify(tokenRepository, never()).deleteByUserAndType(any(), any());
    }

    @Test
    void shouldRejectUpdatingAdminEmailToOneInUse() {
        User other = User.builder().id(UUID.randomUUID()).email("otro@signa.com").build();
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));
        when(userRepository.findFirstByEmailIgnoreCase("otro@signa.com"))
                .thenReturn(Optional.of(other));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () ->
                        adminService.updateAdmin(
                                adminUser.getId(),
                                new UpdateAdminRequest(null, null, "otro@signa.com", null, null)));
        assertEquals("admin@signa.com", adminUser.getEmail());
    }

    @Test
    void shouldNotUpdateAsAdminAnAccountThatIsNotOne() {
        User plain =
                User.builder().id(UUID.randomUUID()).email("u@signa.com").role(Role.USER).build();
        when(userRepository.findById(plain.getId())).thenReturn(Optional.of(plain));

        assertThrows(
                NotFoundException.class,
                () ->
                        adminService.updateAdmin(
                                plain.getId(),
                                new UpdateAdminRequest("X", null, null, null, null)));
    }

    @Test
    void shouldCreateAdminWithItsPhoto() {
        when(userRepository.findFirstByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userSettingsRepository.save(any())).thenReturn(UserSettings.builder().build());

        AdminUserResponse result =
                adminService.createAdmin(
                        new CreateAdminRequest(
                                "foto@signa.com",
                                "Foto",
                                "Admin",
                                "password123",
                                "data:image/jpeg;base64,AAAA"));

        assertEquals("data:image/jpeg;base64,AAAA", result.avatar());
    }

    @Test
    void shouldCreateAdminWithoutPhotoWhenAvatarIsEmpty() {
        when(userRepository.findFirstByEmailIgnoreCase(anyString())).thenReturn(Optional.empty());
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("hashed");
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(userSettingsRepository.save(any())).thenReturn(UserSettings.builder().build());

        AdminUserResponse result =
                adminService.createAdmin(
                        new CreateAdminRequest("sin@signa.com", "Sin", "Foto", "password123", ""));

        assertEquals(null, result.avatar());
    }

    @Test
    void shouldPromoteExistingAccountKeepingItsPassword() {
        User existing =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("existing@signa.com")
                        .username("existing")
                        .name("Nombre")
                        .lastName("Apellido")
                        .passwordHash("own-hash")
                        .role(Role.USER)
                        .enabled(false)
                        .verified(false)
                        .providers(new HashSet<>(Set.of(AuthProvider.LOCAL)))
                        .build();
        when(userRepository.findFirstByEmailIgnoreCase("existing@signa.com"))
                .thenReturn(Optional.of(existing));
        when(userRepository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        AdminUserResponse result =
                adminService.createAdmin(
                        new CreateAdminRequest(
                                "existing@signa.com", "Otro", "Dato", "temporal", null));

        assertEquals(Role.ADMIN, result.role());
        assertEquals("own-hash", existing.getPasswordHash());
        assertEquals("Nombre", existing.getName());
        assertEquals(true, existing.isEnabled());
        assertEquals(true, existing.isVerified());
        verify(passwordEncoder, never()).encode(anyString());
        verify(userSettingsRepository, never()).save(any());
    }

    @Test
    void shouldGivePasswordToPromotedGoogleOnlyAccount() {
        User google =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("google@signa.com")
                        .username("google")
                        .name("Nombre")
                        .lastName("Apellido")
                        .role(Role.USER)
                        .enabled(true)
                        .verified(true)
                        .providers(new HashSet<>(Set.of(AuthProvider.GOOGLE)))
                        .build();
        when(userRepository.findFirstByEmailIgnoreCase("google@signa.com"))
                .thenReturn(Optional.of(google));
        when(userRepository.findById(google.getId())).thenReturn(Optional.of(google));
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());
        when(passwordEncoder.encode("temporal")).thenReturn("hashed");

        adminService.createAdmin(
                new CreateAdminRequest("google@signa.com", "A", "B", "temporal", null));

        assertEquals("hashed", google.getPasswordHash());
        assertEquals(true, google.getProviders().contains(AuthProvider.LOCAL));
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
                new UpdateOrganizationStatusRequest(OrganizationStatus.SUSPENDED);
        adminService.updateOrganizationStatus(orgId, req);

        verify(organizationRepository).save(any());
    }

    private SignReport sampleReport(String meaning, ReportReason reason, ReportStatus status) {
        SignLanguage lang =
                SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();
        Sign sign =
                Sign.builder()
                        .id(UUID.randomUUID())
                        .meaning(meaning)
                        .handedness(Handedness.ONE_HANDED)
                        .signLanguage(lang)
                        .build();

        SignReport report = new SignReport();
        report.setId(UUID.randomUUID());
        report.setSign(sign);
        report.setUser(adminUser);
        report.setStatus(status);
        report.setReason(reason);
        report.setDescription("no se entiende");
        report.setCreatedAt(LocalDateTime.now());
        return report;
    }

    @Test
    void shouldGetSignReports() {
        SignReport report = sampleReport("hola", ReportReason.INCORRECT_SIGN, ReportStatus.PENDING);

        when(signReportRepository.findAll(any(Specification.class))).thenReturn(List.of(report));

        List<AdminSignReportResponse> reports =
                adminService.getSignReports(null, null, null, null, null);

        assertEquals(1, reports.size());
        assertEquals("hola", reports.get(0).signMeaning());
        assertEquals(ReportStatus.PENDING, reports.get(0).status());
        assertEquals("no se entiende", reports.get(0).description());
        assertEquals("admin", reports.get(0).username());
        assertNull(reports.get(0).resolvedBy());
    }

    @Test
    void shouldGetSignReportsWithFilters() {
        LocalDate from = LocalDate.of(2026, 1, 1);
        LocalDate to = LocalDate.of(2026, 2, 1);
        SignReport report = sampleReport("hola", ReportReason.OTHER, ReportStatus.REVIEWED);

        when(signReportRepository.findAll(any(Specification.class))).thenReturn(List.of(report));

        List<AdminSignReportResponse> reports =
                adminService.getSignReports(
                        ReportStatus.REVIEWED, ReportReason.OTHER, "  hola  ", from, to);

        assertEquals(1, reports.size());
        assertEquals("hola", reports.get(0).signMeaning());
    }

    @Test
    void shouldUpdateSignReportAndRecordResolver() {
        UUID reportId = UUID.randomUUID();
        SignReport report = sampleReport("gracias", ReportReason.OTHER, ReportStatus.PENDING);
        report.setId(reportId);

        when(signReportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(signReportRepository.save(any())).thenReturn(report);

        UpdateSignReportRequest req =
                new UpdateSignReportRequest(ReportStatus.RESOLVED, "Verified OK");
        AdminSignReportResponse result = adminService.updateSignReport(reportId, req, adminUser);

        assertEquals(reportId, result.id());
        assertEquals(ReportStatus.RESOLVED, result.status());
        assertEquals("Verified OK", result.adminNote());
        assertEquals("Admin Signa", result.resolvedBy());
        assertNotNull(result.resolvedAt());
        verify(signReportRepository).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenReportDoesNotExist() {
        UUID reportId = UUID.randomUUID();
        when(signReportRepository.findById(reportId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        adminService.updateSignReport(
                                reportId,
                                new UpdateSignReportRequest(ReportStatus.RESOLVED, null),
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
        AdminUserResponse result = adminService.updateUser(adminUser, adminUser.getId(), req);

        assertNotNull(result);
        verify(userRepository).save(any());
    }

    @Test
    void shouldRevokeSessionsWhenDisablingUser() {
        User target = otherUser("target@signa.com");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        adminService.updateUser(adminUser, target.getId(), new UpdateUserRequest(null, false));

        assertEquals(false, target.isEnabled());
        verify(tokenRepository).deleteByUserAndType(target, TokenType.REFRESH);
    }

    @Test
    void shouldNotRevokeSessionsWhenEnablingUser() {
        User target = otherUser("target@signa.com");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));
        when(memberRepository.findByUserId(any())).thenReturn(Optional.empty());

        adminService.updateUser(adminUser, target.getId(), new UpdateUserRequest(null, true));

        verify(tokenRepository, never()).deleteByUserAndType(any(), any());
    }

    @Test
    void shouldRefuseToDisableOrDemoteYourself() {
        when(userRepository.findById(adminUser.getId())).thenReturn(Optional.of(adminUser));

        assertThrows(
                InvalidInputException.class,
                () ->
                        adminService.updateUser(
                                adminUser, adminUser.getId(), new UpdateUserRequest(null, false)));
        assertThrows(
                InvalidInputException.class,
                () ->
                        adminService.updateUser(
                                adminUser,
                                adminUser.getId(),
                                new UpdateUserRequest(Role.USER, null)));
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldDeleteUserWithTheAccountErasure() {
        User target = otherUser("target@signa.com");
        when(userRepository.findById(target.getId())).thenReturn(Optional.of(target));

        adminService.deleteUser(adminUser, target.getId());

        verify(userService).deleteAccount(target);
    }

    @Test
    void shouldRefuseToDeleteYourself() {
        assertThrows(
                InvalidInputException.class,
                () -> adminService.deleteUser(adminUser, adminUser.getId()));
        verify(userService, never()).deleteAccount(any());
    }

    @Test
    void shouldTreatAnAlreadyDeletedUserAsNotFound() {
        User deleted = otherUser("deleted_1@signa.invalid");
        when(userRepository.findById(deleted.getId())).thenReturn(Optional.of(deleted));

        assertThrows(
                NotFoundException.class, () -> adminService.deleteUser(adminUser, deleted.getId()));
        verify(userService, never()).deleteAccount(any());
    }

    private User otherUser(String email) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .username("other")
                .name("Otra")
                .lastName("Persona")
                .role(Role.USER)
                .enabled(true)
                .verified(true)
                .build();
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

    private Sign signFixture(UUID id) {
        SignLanguage lang =
                SignLanguage.builder().id(UUID.randomUUID()).code("LSA").name("LSA").build();
        return Sign.builder()
                .id(id)
                .meaning("hola")
                .handedness(Handedness.ONE_HANDED)
                .signLanguage(lang)
                .build();
    }

    @Test
    void shouldGetSignDetail() {
        UUID id = UUID.randomUUID();
        Sign sign = signFixture(id);
        sign.setYoutubeUrl("https://youtu.be/abc123");
        when(signRepository.findById(id)).thenReturn(Optional.of(sign));
        when(signReportRepository.countBySignIdAndStatusIn(any(), any())).thenReturn(2L);

        var result = adminService.getSign(id);

        assertEquals("hola", result.meaning());
        assertEquals("LSA", result.signLanguageCode());
        assertEquals("https://youtu.be/abc123", result.youtubeUrl());
        assertEquals(2L, result.openReports());
    }

    @Test
    void shouldThrowNotFoundWhenGettingMissingSign() {
        UUID id = UUID.randomUUID();
        when(signRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> adminService.getSign(id));
    }

    @Test
    void shouldUpdateSignYoutubeUrl() {
        UUID id = UUID.randomUUID();
        Sign sign = signFixture(id);
        when(signRepository.findById(id)).thenReturn(Optional.of(sign));
        when(signReportRepository.countBySignIdAndStatusIn(any(), any())).thenReturn(0L);

        var result = adminService.updateSign(id, "https://www.youtube.com/watch?v=xyz");

        assertEquals("https://www.youtube.com/watch?v=xyz", sign.getYoutubeUrl());
        assertEquals("https://www.youtube.com/watch?v=xyz", result.youtubeUrl());
        verify(signRepository).save(sign);
    }

    @Test
    void shouldClearSignYoutubeUrlWhenBlank() {
        UUID id = UUID.randomUUID();
        Sign sign = signFixture(id);
        sign.setYoutubeUrl("https://youtu.be/old");
        when(signRepository.findById(id)).thenReturn(Optional.of(sign));
        when(signReportRepository.countBySignIdAndStatusIn(any(), any())).thenReturn(0L);

        adminService.updateSign(id, "   ");

        assertNull(sign.getYoutubeUrl());
        verify(signRepository).save(sign);
    }

    @Test
    void shouldRejectNonYoutubeUrl() {
        UUID id = UUID.randomUUID();
        Sign sign = signFixture(id);
        when(signRepository.findById(id)).thenReturn(Optional.of(sign));

        assertThrows(
                InvalidInputException.class,
                () -> adminService.updateSign(id, "https://vimeo.com/123"));
    }

    @Test
    void shouldThrowNotFoundWhenUpdatingMissingSign() {
        UUID id = UUID.randomUUID();
        when(signRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> adminService.updateSign(id, "https://youtu.be/abc"));
    }
}
