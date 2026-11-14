package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.InviteCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    @Modifying
    @Query("delete from InviteCode c where c.organization.id = :organizationId")
    void deleteByOrganizationId(@Param("organizationId") UUID organizationId);

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"organization"})
    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByOrganizationId(UUID organizationId);

    Optional<InviteCode> findByIdAndOrganizationId(UUID id, UUID organizationId);
}
