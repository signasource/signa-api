package com.signasource.signa_api.gamification.repository;

import com.signasource.signa_api.gamification.entity.GemPurchase;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GemPurchaseRepository extends JpaRepository<GemPurchase, UUID> {

    Optional<GemPurchase> findByPurchaseToken(String purchaseToken);
}
