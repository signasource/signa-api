package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OrganizationMemberService {

    private final OrganizationMemberRepository memberRepository;
    private final OrganizationAccessService accessService;
    private final OrganizationEnrollmentService enrollmentService;

    /**
     * Takes a participant out of the organization: they lose access to the contracted courses but
     * keep their progress, which is restored if they join again.
     */
    @Transactional
    public void removeMember(User actor, UUID organizationId, UUID userId) {
        accessService.requireManage(actor, organizationId);

        OrganizationMember member =
                memberRepository
                        .findByOrganizationIdAndUserId(organizationId, userId)
                        .filter(m -> m.getStatus() == MemberStatus.ACTIVE)
                        .orElseThrow(() -> new NotFoundException("Member not found"));
        if (member.getRole() == MemberRole.ADMIN) {
            throw new InvalidInputException("Organization admins cannot be removed as members");
        }

        member.setStatus(MemberStatus.REMOVED);
        member.setRemovedAt(Instant.now());
        memberRepository.save(member);

        enrollmentService.revokeAllFromUser(member.getUser(), organizationId);
    }
}
