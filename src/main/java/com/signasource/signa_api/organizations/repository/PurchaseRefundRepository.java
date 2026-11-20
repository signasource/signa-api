package com.signasource.signa_api.organizations.repository;

import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PurchaseRefundRepository extends JpaRepository<PurchaseRefund, UUID> {

    Optional<PurchaseRefund> findByPaymentId(String paymentId);

    List<PurchaseRefund> findByPurchaseIdOrderByCreatedAtAsc(UUID purchaseId);
}
