package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.organizations.dto.CreateInviteCodeRequest;
import com.signasource.signa_api.organizations.dto.InviteByEmailRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.InviteCodeRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
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
class InviteCodeServiceTest {

    @Mock private InviteCodeRepository inviteCodeRepository;
    @Mock private OrganizationRepository organizationRepository;
    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationEnrollmentService enrollmentService;
    @Mock private EmailService emailService;

    @InjectMocks private InviteCodeService inviteCodeService;

    private User user;
    private Organization organization;
    private Course course;
    private InviteCode inviteCode;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ana@hospital.com");

        organization =
                Organization.builder().id(UUID.randomUUID()).name("Hospital San Martin").build();
        course =
                Course.builder()
                        .id(UUID.randomUUID())
                        .name("LSA para Salud")
                        .signLanguage(SignLanguage.builder().code("LSA").build())
                        .build();

        inviteCode =
                InviteCode.builder()
                        .id(UUID.randomUUID())
                        .code("HSMT2026")
                        .organization(organization)
                        .active(true)
                        .useCount(0)
                        .build();
    }

    private void stubOrganizationLookupAndSave() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(inviteCodeRepository.existsByCode(any())).thenReturn(false);
        when(inviteCodeRepository.save(any(InviteCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void shouldGenerateAndSaveAnUnrestrictedCode() {
        stubOrganizationLookupAndSave();

        InviteCodeResponse response =
                inviteCodeService.createInviteCode(
                        user, organization.getId(), new CreateInviteCodeRequest(null, 5));

        verify(accessService).requireManage(user, organization.getId());
        assertEquals(8, response.code().length());
        assertEquals(5, response.maxUses());
        assertNull(response.email());
    }

    @Test
    void shouldRetryWhenGeneratedCodeCollides() {
        when(organizationRepository.findById(organization.getId()))
                .thenReturn(Optional.of(organization));
        when(inviteCodeRepository.existsByCode(any())).thenReturn(true, false);
        when(inviteCodeRepository.save(any(InviteCode.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        inviteCodeService.createInviteCode(
                user, organization.getId(), new CreateInviteCodeRequest(null, null));

        verify(inviteCodeRepository, times(2)).existsByCode(any());
    }

    @Test
    void shouldNotCreateCodeWhenCallerCannotManageTheOrganization() {
        doThrow(new ForbiddenException("no"))
                .when(accessService)
                .requireManage(user, organization.getId());

        assertThrows(
                ForbiddenException.class,
                () ->
                        inviteCodeService.createInviteCode(
                                user,
                                organization.getId(),
                                new CreateInviteCodeRequest(null, null)));
        verify(inviteCodeRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenOrganizationDoesNotExist() {
        when(organizationRepository.findById(organization.getId())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        inviteCodeService.createInviteCode(
                                user,
                                organization.getId(),
                                new CreateInviteCodeRequest(null, null)));
    }

    @Test
    void shouldCreateSingleUseCodeAndMailItWhenInvitingByEmail() {
        stubOrganizationLookupAndSave();

        InviteCodeResponse response =
                inviteCodeService.inviteByEmail(
                        user,
                        organization.getId(),
                        new InviteByEmailRequest("  Nuevo@Hospital.com ", null));

        assertEquals("nuevo@hospital.com", response.email());
        assertEquals(1, response.maxUses());
        verify(emailService)
                .sendOrganizationInviteEmail(
                        "nuevo@hospital.com", "Hospital San Martin", response.code());
    }

    @Test
    void shouldListTheOrganizationsCodes() {
        when(inviteCodeRepository.findByOrganizationId(organization.getId()))
                .thenReturn(List.of(inviteCode));

        List<InviteCodeResponse> result =
                inviteCodeService.getOrganizationInviteCodes(user, organization.getId());

        verify(accessService).requireManage(user, organization.getId());
        assertEquals(1, result.size());
        assertEquals("HSMT2026", result.get(0).code());
    }

    @Test
    void shouldDeactivateCode() {
        when(inviteCodeRepository.findByIdAndOrganizationId(
                        inviteCode.getId(), organization.getId()))
                .thenReturn(Optional.of(inviteCode));

        inviteCodeService.deactivateInviteCode(user, organization.getId(), inviteCode.getId());

        assertFalse(inviteCode.isActive());
        verify(inviteCodeRepository).save(inviteCode);
    }

    @Test
    void shouldThrowNotFoundWhenDeactivatingUnknownCode() {
        when(inviteCodeRepository.findByIdAndOrganizationId(any(), any()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () ->
                        inviteCodeService.deactivateInviteCode(
                                user, organization.getId(), UUID.randomUUID()));
    }

    @Test
    void shouldJoinOrganizationAndGrantContractedCoursesOnRedeem() {
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(enrollmentService.grantContractedCourses(user, organization, null))
                .thenReturn(List.of(course));

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, " hsmt-2026 ");

        assertFalse(response.alreadyMember());
        assertEquals("Hospital San Martin", response.organizationName());
        assertEquals(1, response.courses().size());
        assertEquals(1, inviteCode.getUseCount());
        assertEquals(course, user.getCurrentCourse());

        ArgumentCaptor<OrganizationMember> captor =
                ArgumentCaptor.forClass(OrganizationMember.class);
        verify(memberRepository).save(captor.capture());
        assertEquals(MemberRole.MEMBER, captor.getValue().getRole());
        assertEquals(MemberStatus.ACTIVE, captor.getValue().getStatus());
        assertEquals(user, captor.getValue().getUser());
        verify(userRepository).save(user);
    }

    @Test
    void shouldReactivateRemovedMemberInsteadOfCreatingANewRow() {
        OrganizationMember removed =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.REMOVED)
                        .removedAt(Instant.now())
                        .build();
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(removed));
        when(enrollmentService.grantContractedCourses(any(), any(), any())).thenReturn(List.of());

        inviteCodeService.redeem(user, "HSMT2026");

        assertEquals(MemberStatus.ACTIVE, removed.getStatus());
        assertNull(removed.getRemovedAt());
        verify(memberRepository).save(removed);
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldBeIdempotentForAnExistingActiveMember() {
        OrganizationMember active =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(active));
        when(enrollmentService.grantContractedCourses(any(), any(), any())).thenReturn(List.of());

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertTrue(response.alreadyMember());
        assertEquals(0, inviteCode.getUseCount());
        verify(memberRepository, never()).save(any());
        verify(inviteCodeRepository, never()).save(any());
    }

    @Test
    void shouldCreateAdminInviteBoundToTheEmailAndMailAPanelLink() {
        stubOrganizationLookupAndSave();

        InviteCodeResponse response =
                inviteCodeService.inviteAdminByEmail(
                        user,
                        organization.getId(),
                        new InviteByEmailRequest(" Boss@Hospital.com ", null));

        verify(accessService).requireManage(user, organization.getId());
        assertEquals(MemberRole.ADMIN, response.memberRole());
        assertEquals("boss@hospital.com", response.email());
        assertEquals(1, response.maxUses());
        verify(emailService)
                .sendOrganizationAdminInviteEmail(
                        "boss@hospital.com", "Hospital San Martin", response.code());
        verify(emailService, never()).sendOrganizationInviteEmail(any(), any(), any());
    }

    @Test
    void shouldMakeTheRedeemerAnAdminWithoutEnrollingThemInCourses() {
        user.setRole(Role.USER);
        inviteCode.setMemberRole(MemberRole.ADMIN);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertEquals(MemberRole.ADMIN, response.role());
        assertTrue(response.courses().isEmpty());
        assertFalse(response.alreadyMember());
        assertEquals(Role.ORG_ADMIN, user.getRole());
        assertEquals(1, inviteCode.getUseCount());
        ArgumentCaptor<OrganizationMember> captor =
                ArgumentCaptor.forClass(OrganizationMember.class);
        verify(memberRepository).save(captor.capture());
        assertEquals(MemberRole.ADMIN, captor.getValue().getRole());
        verify(userRepository).save(user);
        verify(enrollmentService, never()).grantContractedCourses(any(), any(), any());
    }

    @Test
    void shouldPromoteAnExistingParticipantWhoRedeemsAnAdminCode() {
        user.setRole(Role.USER);
        inviteCode.setMemberRole(MemberRole.ADMIN);
        OrganizationMember participant =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(participant));

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertFalse(response.alreadyMember());
        assertEquals(MemberRole.ADMIN, participant.getRole());
        assertEquals(Role.ORG_ADMIN, user.getRole());
    }

    @Test
    void shouldNotTouchTheUserRoleWhenTheyAreAlreadyAnAdmin() {
        user.setRole(Role.ORG_ADMIN);
        inviteCode.setMemberRole(MemberRole.ADMIN);
        OrganizationMember admin =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.ADMIN)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(admin));

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertTrue(response.alreadyMember());
        assertEquals(0, inviteCode.getUseCount());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldNotDemoteAnAdminWhoRedeemsAParticipantCode() {
        user.setRole(Role.ORG_ADMIN);
        OrganizationMember admin =
                OrganizationMember.builder()
                        .user(user)
                        .organization(organization)
                        .role(MemberRole.ADMIN)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(admin));

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertTrue(response.alreadyMember());
        assertEquals(MemberRole.ADMIN, admin.getRole());
        assertEquals(MemberRole.ADMIN, response.role());
        verify(enrollmentService, never()).grantContractedCourses(any(), any(), any());
    }

    @Test
    void shouldRejectRedeemWhenUserBelongsToAnotherOrganization() {
        Organization other = Organization.builder().id(UUID.randomUUID()).name("Other").build();
        OrganizationMember member =
                OrganizationMember.builder()
                        .user(user)
                        .organization(other)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build();
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.of(member));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> inviteCodeService.redeem(user, "HSMT2026"));
        verify(enrollmentService, never()).grantContractedCourses(any(), any(), any());
    }

    @Test
    void shouldPassTheCodesExpiryAsAccessExpiry() {
        Instant expiry = Instant.now().plus(30, ChronoUnit.DAYS);
        inviteCode.setExpiresAt(expiry);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(enrollmentService.grantContractedCourses(user, organization, expiry))
                .thenReturn(List.of());

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertEquals(expiry, response.accessExpiresAt());
    }

    @Test
    void shouldRejectUnknownCode() {
        when(inviteCodeRepository.findByCode("NOPE")).thenReturn(Optional.empty());

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "nope"));
    }

    @Test
    void shouldRejectInactiveCode() {
        inviteCode.setActive(false);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
    }

    @Test
    void shouldRejectExpiredCode() {
        inviteCode.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
    }

    @Test
    void shouldRejectExhaustedCode() {
        inviteCode.setMaxUses(2);
        inviteCode.setUseCount(2);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
    }

    @Test
    void shouldRejectEmailInviteRedeemedByAnotherUser() {
        inviteCode.setEmail("someone.else@hospital.com");
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldAcceptEmailInviteIgnoringCase() {
        inviteCode.setEmail("ANA@hospital.com");
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());
        when(enrollmentService.grantContractedCourses(eq(user), eq(organization), any()))
                .thenReturn(List.of());

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertNotNull(response);
        assertFalse(response.alreadyMember());
    }
}
