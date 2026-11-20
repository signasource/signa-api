package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import com.signasource.signa_api.organizations.entity.RefundStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseRefundRepository extends JpaRepository<PurchaseRefund, UUID> {

    Optional<PurchaseRefund> findByPaymentId(String paymentId);

    List<PurchaseRefund> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);

    @EntityGraph(
            attributePaths = {
                "purchase",
                "purchase.course",
                "purchase.organization",
                "requestedBy"
            })
    List<PurchaseRefund> findByStatusOrderByRefundedAtDesc(RefundStatus status);
}
