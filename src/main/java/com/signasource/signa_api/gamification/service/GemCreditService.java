package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.entity.GemPurchase;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemPurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.gamification.service.GooglePlayPurchaseVerifier.VerifiedProductPurchase;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of a gem-pack redemption, split from {@link GemPurchaseService} so the Google
 * call happens outside the transaction and so the transactional proxy is not bypassed by a
 * self-invocation.
 */
@Service
@RequiredArgsConstructor
public class GemCreditService {

    private final GemPurchaseRepository gemPurchaseRepository;
    private final UserStatsRepository userStatsRepository;
    private final PurchaseService purchaseService;

    /**
     * Credits the pack's gems and records the purchase. The unique index on {@code purchase_token}
     * makes a concurrent duplicate fail with a {@code DataIntegrityViolationException}, which the
     * caller resolves by re-reading the winner.
     */
    @Transactional
    public GemPurchase credit(
            User user, GemPack pack, String purchaseToken, VerifiedProductPurchase verified) {
        UserStats stats = purchaseService.getOrCreateStats(user);
        stats.setGems(stats.getGems() + pack.getGems());
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);

        return gemPurchaseRepository.save(
                GemPurchase.builder()
                        .user(user)
                        .gemPack(pack)
                        .productId(pack.getProductId())
                        .purchaseToken(purchaseToken)
                        .orderId(verified.orderId())
                        .gemsGranted(pack.getGems())
                        .purchasedAt(verified.purchaseTime())
                        .verifiedAt(Instant.now())
                        .build());
    }
}
