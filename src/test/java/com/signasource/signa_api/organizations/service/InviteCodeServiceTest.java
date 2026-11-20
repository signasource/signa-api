package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.service.EmailService;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.organizations.dto.InviteByEmailRequest;
import com.signasource.signa_api.organizations.dto.InviteCodeResponse;
import com.signasource.signa_api.organizations.dto.RedeemInviteCodeResponse;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
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
    @Mock private EmailService emailService;

    @InjectMocks private InviteCodeService inviteCodeService;

    private User user;
    private Organization organization;
    private InviteCode inviteCode;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail("ana@hospital.com");

        organization =
                Organization.builder()
                        .id(UUID.randomUUID())
                        .name("Hospital San Martin")
                        .status(OrganizationStatus.ACTIVE)
                        .build();

        inviteCode =
                InviteCode.builder()
                        .id(UUID.randomUUID())
                        .code("HSMT2026")
                        .organization(organization)
                        .memberRole(MemberRole.ADMIN)
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
    }

    @Test
    void shouldRejectLegacyParticipantCodes() {
        inviteCode.setMemberRole(MemberRole.MEMBER);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));

        assertThrows(InvalidInputException.class, () -> inviteCodeService.redeem(user, "HSMT2026"));
        verify(memberRepository, never()).save(any());
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

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertNotNull(response);
        assertFalse(response.alreadyMember());
    }

    @Test
    void shouldLetAnAdminJoinAPendingOrganization() {
        organization.setStatus(OrganizationStatus.PENDING);
        inviteCode.setMemberRole(MemberRole.ADMIN);
        user.setRole(Role.USER);
        when(inviteCodeRepository.findByCode("HSMT2026")).thenReturn(Optional.of(inviteCode));
        when(memberRepository.findByUserId(user.getId())).thenReturn(Optional.empty());

        RedeemInviteCodeResponse response = inviteCodeService.redeem(user, "HSMT2026");

        assertEquals(MemberRole.ADMIN, response.role());
        assertEquals(Role.ORG_ADMIN, user.getRole());
    }
}
