package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.dto.GemPackResponse;
import com.signasource.signa_api.gamification.dto.GemPurchaseResponse;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.entity.GemPurchase;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
import com.signasource.signa_api.gamification.repository.GemPurchaseRepository;
import com.signasource.signa_api.gamification.service.GooglePlayPurchaseVerifier.VerifiedProductPurchase;
import com.signasource.signa_api.users.entity.User;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Gem packs bought with real money through Google Play. */
@Service
@RequiredArgsConstructor
public class GemPurchaseService {

    private final GemPackRepository gemPackRepository;
    private final GemPurchaseRepository gemPurchaseRepository;
    private final GooglePlayPurchaseVerifier verifier;
    private final GemCreditService gemCreditService;
    private final PurchaseService purchaseService;

    @Transactional(readOnly = true)
    public List<GemPackResponse> getPacks() {
        return gemPackRepository.findByActiveTrueOrderBySortOrderAsc().stream()
                .map(GemPackResponse::from)
                .toList();
    }

    /**
     * Verifies a Google Play purchase token and credits the pack's gems exactly once. Deliberately
     * not transactional: the Google call is network I/O; the credit itself is transactional in
     * {@link GemCreditService}.
     */
    public GemPurchaseResponse redeem(User user, String productId, String purchaseToken) {
        purchaseService.ensureEnabled(user);

        Optional<GemPurchase> existing = gemPurchaseRepository.findByPurchaseToken(purchaseToken);
        if (existing.isPresent()) {
            return alreadyRedeemed(user, existing.get());
        }

        GemPack pack =
                gemPackRepository
                        .findByProductIdAndActiveTrue(productId)
                        .orElseThrow(() -> new NotFoundException("Gem pack not found"));

        VerifiedProductPurchase verified = verifier.verify(productId, purchaseToken);
        if (verified.isPending()) {
            throw new InvalidInputException("Purchase is still pending");
        }
        if (!verified.isPurchased()) {
            throw new InvalidInputException("Purchase was not completed");
        }
        if (verified.isConsumed()) {
            // Consumed by the app without ever being credited here: nothing we can trust anymore.
            throw new ResourceAlreadyInUseException("Purchase token already consumed");
        }

        GemPurchase purchase;
        try {
            purchase = gemCreditService.credit(user, pack, purchaseToken, verified);
        } catch (DataIntegrityViolationException race) {
            // Two requests with the same token raced; the other one won and already credited.
            GemPurchase winner =
                    gemPurchaseRepository
                            .findByPurchaseToken(purchaseToken)
                            .orElseThrow(() -> race);
            return alreadyRedeemed(user, winner);
        }

        return GemPurchaseResponse.from(purchase, statsOf(user), false);
    }

    private GemPurchaseResponse alreadyRedeemed(User user, GemPurchase purchase) {
        if (!purchase.getUser().getId().equals(user.getId())) {
            throw new ResourceAlreadyInUseException("Purchase token already redeemed");
        }
        return GemPurchaseResponse.from(purchase, statsOf(user), true);
    }

    private UserStats statsOf(User user) {
        return purchaseService.getOrCreateStats(user);
    }
}
