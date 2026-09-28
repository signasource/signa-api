package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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

    @Query(
            value =
                    "SELECT m FROM OrganizationMember m JOIN FETCH m.user u "
                            + "WHERE m.organization.id = :organizationId AND m.role = :role "
                            + "AND m.status IN :statuses "
                            + "AND (LOWER(u.name) LIKE :pattern OR LOWER(u.lastName) LIKE :pattern "
                            + "OR LOWER(u.email) LIKE :pattern)",
            countQuery =
                    "SELECT COUNT(m) FROM OrganizationMember m JOIN m.user u "
                            + "WHERE m.organization.id = :organizationId AND m.role = :role "
                            + "AND m.status IN :statuses "
                            + "AND (LOWER(u.name) LIKE :pattern OR LOWER(u.lastName) LIKE :pattern "
                            + "OR LOWER(u.email) LIKE :pattern)")
    Page<OrganizationMember> search(
            @Param("organizationId") UUID organizationId,
            @Param("role") MemberRole role,
            @Param("statuses") Collection<MemberStatus> statuses,
            @Param("pattern") String pattern,
            Pageable pageable);
}
