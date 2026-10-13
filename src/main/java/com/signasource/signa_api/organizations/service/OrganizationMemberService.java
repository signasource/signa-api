package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationMemberService {

    private final OrganizationMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationEnrollmentService enrollmentService;

    /**
     * Takes a participant out of the organization: they lose access to the contracted courses but
     * keep their progress, which is restored if they join again.
     */
    @Transactional
    public void removeMember(User actor, UUID organizationId, UUID userId) {
        accessService.requireManage(actor, organizationId);

        OrganizationMember member = findActive(organizationId, userId);
        if (member.getRole() == MemberRole.ADMIN) {
            throw new InvalidInputException("Organization admins cannot be removed as members");
        }
        remove(member);
    }

    /** A participant leaving on their own, which frees them to join another organization. */
    @Transactional
    public void leave(User user) {
        OrganizationMember member =
                memberRepository
                        .findByUserId(user.getId())
                        .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "User does not belong to an organization"));
        if (member.getRole() == MemberRole.ADMIN) {
            throw new InvalidInputException(
                    "Organization admins cannot leave; ask SIGNA to remove the admin role");
        }
        remove(member);
    }

    /** Revokes an admin's access to the panel; their user account stays untouched otherwise. */
    @Transactional
    public void removeAdmin(UUID organizationId, UUID userId) {
        OrganizationMember member = findActive(organizationId, userId);
        if (member.getRole() != MemberRole.ADMIN) {
            throw new NotFoundException("Admin not found");
        }
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(Instant.now());
        memberRepository.save(member);

        User user = member.getUser();
        if (user.getRole() == Role.ORG_ADMIN) {
            user.setRole(Role.USER);
            userRepository.save(user);
        }
    }

    private OrganizationMember findActive(UUID organizationId, UUID userId) {
        return memberRepository
                .findByOrganizationIdAndUserId(organizationId, userId)
                .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                .orElseThrow(() -> new NotFoundException("Member not found"));
    }

    private void remove(OrganizationMember member) {
        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(Instant.now());
        memberRepository.save(member);

        enrollmentService.revokeAllFromUser(member.getUser(), member.getOrganization().getId());
    }
}
