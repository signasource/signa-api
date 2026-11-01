package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

@Repository
public interface CoursePurchaseRepository extends JpaRepository<CoursePurchase, UUID> {

    @EntityGraph(attributePaths = {"course"})
    List<CoursePurchase> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    @EntityGraph(attributePaths = {"course", "organization"})
    Optional<CoursePurchase> findByIdAndOrganizationId(UUID id, UUID organizationId);

    /** Locks the row so the webhook and the panel's sync cannot confirm the same payment twice. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"course", "organization"})
    Optional<CoursePurchase> findForUpdateById(UUID id);

    @EntityGraph(attributePaths = {"course", "organization"})
    Optional<CoursePurchase> findByPaymentReference(String paymentReference);

    /** The purchase a payment paid, locked (see {@link #findForUpdateById}). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"course", "organization"})
    Optional<CoursePurchase> findForUpdateByPaymentReference(String paymentReference);

    @EntityGraph(attributePaths = {"course", "organization"})
    List<CoursePurchase> findByStatusOrderByCreatedAtAsc(PurchaseStatus status);

    @EntityGraph(attributePaths = {"course", "organization"})
    List<CoursePurchase> findAllByOrderByCreatedAtDesc();
}
