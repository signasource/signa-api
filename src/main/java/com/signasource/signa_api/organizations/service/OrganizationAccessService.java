package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.ForbiddenException;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Scopes organization endpoints: SIGNA admins manage any org, org admins only their own. */
@Service
@RequiredArgsConstructor
public class OrganizationAccessService {

    private final OrganizationMemberRepository memberRepository;

    @Transactional(readOnly = true)
    public void requireManage(User actor, UUID organizationId) {
        if (actor.getRole() == Role.ADMIN) {
            return;
        }
        boolean isOrgAdmin =
                actor.getRole() == Role.ORG_ADMIN
                        && memberRepository.existsByOrganizationIdAndUserIdAndRoleAndStatus(
                                organizationId,
                                actor.getId(),
                                MemberRole.ADMIN,
                                MemberStatus.ACTIVE);
        if (!isOrgAdmin) {
            throw new ForbiddenException("You cannot manage this organization");
        }
    }
}
