package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.RefundRequest;
import com.signasource.signa_api.organizations.entity.RefundRequestStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RefundRequestRepository extends JpaRepository<RefundRequest, UUID> {

    @EntityGraph(attributePaths = {"requestedBy"})
    List<RefundRequest> findAllByOrderByCreatedAtDesc();

    long countByStatus(RefundRequestStatus status);

    @EntityGraph(attributePaths = {"requestedBy"})
    List<RefundRequest> findByPurchaseTypeAndPurchaseId(String purchaseType, UUID purchaseId);
}
