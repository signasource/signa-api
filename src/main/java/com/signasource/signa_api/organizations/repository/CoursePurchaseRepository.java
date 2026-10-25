package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePurchaseRepository extends JpaRepository<CoursePurchase, UUID> {

    @EntityGraph(attributePaths = {"course"})
    List<CoursePurchase> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    @EntityGraph(attributePaths = {"course", "organization"})
    Optional<CoursePurchase> findByIdAndOrganizationId(UUID id, UUID organizationId);

    @EntityGraph(attributePaths = {"course", "organization"})
    Optional<CoursePurchase> findWithDetailsById(UUID id);

    @EntityGraph(attributePaths = {"course", "organization"})
    List<CoursePurchase> findByStatusOrderByCreatedAtAsc(PurchaseStatus status);

    @EntityGraph(attributePaths = {"course", "organization"})
    List<CoursePurchase> findAllByOrderByCreatedAtDesc();
}
