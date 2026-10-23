package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationMemberServiceTest {

    @Mock private OrganizationMemberRepository memberRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrganizationAccessService accessService;
    @Mock private OrganizationEnrollmentService enrollmentService;

    @InjectMocks private OrganizationMemberService memberService;

    private User actor;
    private User target;
    private UUID organizationId;

    @BeforeEach
    void setUp() {
        actor = new User();
        actor.setId(UUID.randomUUID());
        target = new User();
        target.setId(UUID.randomUUID());
        organizationId = UUID.randomUUID();
    }

    private OrganizationMember member(MemberRole role, MemberStatus status) {
        return OrganizationMember.builder()
                .user(target)
                .organization(Organization.builder().id(organizationId).build())
                .role(role)
                .status(status)
                .build();
    }

    @Test
    void shouldMarkMemberRemovedAndRevokeTheirAccess() {
        OrganizationMember member = member(MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.of(member));

        memberService.removeMember(actor, organizationId, target.getId());

        assertEquals(MemberStatus.REMOVED, member.getStatus());
        assertNotNull(member.getRemovedAt());
        verify(accessService).requireManage(actor, organizationId);
        verify(memberRepository).save(member);
        verify(enrollmentService).revokeAllFromUser(target, organizationId);
    }

    @Test
    void shouldThrowNotFoundWhenMemberDoesNotExist() {
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> memberService.removeMember(actor, organizationId, target.getId()));
    }

    @Test
    void shouldThrowNotFoundWhenMemberWasAlreadyRemoved() {
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.of(member(MemberRole.MEMBER, MemberStatus.REMOVED)));

        assertThrows(
                NotFoundException.class,
                () -> memberService.removeMember(actor, organizationId, target.getId()));
    }

    @Test
    void shouldNotRemoveOrganizationAdmins() {
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.ACTIVE)));

        assertThrows(
                InvalidInputException.class,
                () -> memberService.removeMember(actor, organizationId, target.getId()));
        verify(memberRepository, never()).save(any());
        verify(enrollmentService, never()).revokeAllFromUser(any(), any());
    }

    @Test
    void shouldLetAParticipantLeaveTheirOrganization() {
        OrganizationMember member = member(MemberRole.MEMBER, MemberStatus.ACTIVE);
        when(memberRepository.findByUserId(target.getId())).thenReturn(Optional.of(member));

        memberService.leave(target);

        assertEquals(MemberStatus.REMOVED, member.getStatus());
        assertNotNull(member.getRemovedAt());
        verify(enrollmentService).revokeAllFromUser(target, organizationId);
    }

    @Test
    void shouldNotLetAnAdminLeaveTheirOrganization() {
        when(memberRepository.findByUserId(target.getId()))
                .thenReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.ACTIVE)));

        assertThrows(InvalidInputException.class, () -> memberService.leave(target));
        verify(memberRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenLeavingWithoutAnActiveMembership() {
        when(memberRepository.findByUserId(target.getId()))
                .thenReturn(Optional.of(member(MemberRole.MEMBER, MemberStatus.REMOVED)));

        assertThrows(NotFoundException.class, () -> memberService.leave(target));
    }

    @Test
    void shouldRemoveAdminAndDemoteTheirPanelRole() {
        target.setRole(Role.ORG_ADMIN);
        OrganizationMember admin = member(MemberRole.ADMIN, MemberStatus.ACTIVE);
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.of(admin));

        memberService.removeAdmin(organizationId, target.getId());

        assertEquals(MemberStatus.REMOVED, admin.getStatus());
        assertNotNull(admin.getRemovedAt());
        assertEquals(Role.USER, target.getRole());
        verify(memberRepository).save(admin);
        verify(userRepository).save(target);
        verifyNoInteractions(enrollmentService);
    }

    @Test
    void shouldKeepSignaAdminRoleWhenRemovingThemAsOrganizationAdmin() {
        target.setRole(Role.ADMIN);
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.of(member(MemberRole.ADMIN, MemberStatus.ACTIVE)));

        memberService.removeAdmin(organizationId, target.getId());

        assertEquals(Role.ADMIN, target.getRole());
        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenRemovingAdminWhoIsAParticipant() {
        when(memberRepository.findByOrganizationIdAndUserId(organizationId, target.getId()))
                .thenReturn(Optional.of(member(MemberRole.MEMBER, MemberStatus.ACTIVE)));

        assertThrows(
                NotFoundException.class,
                () -> memberService.removeAdmin(organizationId, target.getId()));
        verify(memberRepository, never()).save(any());
    }
}
