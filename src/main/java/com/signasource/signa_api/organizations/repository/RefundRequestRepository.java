package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    @EntityGraph(attributePaths = {"requestedBy"})
    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(RefundRequestStatus status);

    @EntityGraph(attributePaths = {"requestedBy"})
    List<RefundRequest> findByPurchaseTypeAndPurchaseId(String purchaseType, UUID purchaseId);

    List<RefundRequest> findByRequestedByIdOrderByCreatedAtDesc(UUID userId);

    List<RefundRequest> findByRequestedByIdAndPurchaseType(UUID requestedById, String purchaseType);

    boolean existsByPurchaseTypeAndPurchaseIdAndStatus(
            String purchaseType, UUID purchaseId, RefundRequestStatus status);

    @Query(
            "SELECT r.purchaseId FROM RefundRequest r"
                    + " WHERE r.purchaseType = :type AND r.status = :status AND r.purchaseId IN :ids")
    List<UUID> findPurchaseIdsWithPendingRequest(
            @Param("type") String type,
            @Param("status") RefundRequestStatus status,
            @Param("ids") Collection<UUID> ids);
}
