package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, UUID> {

    @EntityGraph(attributePaths = {"organization"})
    Optional<OrganizationMember> findByUserId(UUID userId);

    Optional<OrganizationMember> findByOrganizationIdAndUserId(UUID organizationId, UUID userId);

    boolean existsByOrganizationIdAndUserIdAndRoleAndStatus(
            UUID organizationId, UUID userId, MemberRole role, MemberStatus status);

    @EntityGraph(attributePaths = {"user"})
    List<OrganizationMember> findByOrganizationIdAndRoleAndStatus(
            UUID organizationId, MemberRole role, MemberStatus status);
}
