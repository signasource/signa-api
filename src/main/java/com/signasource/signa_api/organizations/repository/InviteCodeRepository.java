package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.InviteCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface InviteCodeRepository extends JpaRepository<InviteCode, UUID> {

    boolean existsByCode(String code);

    @EntityGraph(attributePaths = {"organization", "course"})
    Optional<InviteCode> findByCode(String code);

    List<InviteCode> findByOrganizationId(UUID organizationId);
}
