package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.gamification.service.GooglePlayPurchaseVerifier.VerifiedProductPurchase;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class GemPurchaseServiceTest {

    private static final String PRODUCT_ID = "gems_pack_300";
    private static final String TOKEN = "purchase-token-abc";

    @Mock private GemPackRepository gemPackRepository;
    @Mock private GemPurchaseRepository gemPurchaseRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private GooglePlayPurchaseVerifier verifier;

    private GemPurchaseService gemPurchaseService;
    private GemCreditService gemCreditService;

    private User user;
    private UserStats stats;
    private GemPack pack;

    @BeforeEach
    void setUp() {
        user = user("user@example.com", "testuser");
        stats = UserStats.builder().id(UUID.randomUUID()).user(user).gems(100).build();
        pack =
                GemPack.builder()
                        .id(UUID.randomUUID())
                        .productId(PRODUCT_ID)
                        .gems(300)
                        .sortOrder(2)
                        .build();

        PurchaseService purchaseService = new PurchaseService(null, null, userStatsRepository);
        gemCreditService =
                new GemCreditService(gemPurchaseRepository, userStatsRepository, purchaseService);
        gemPurchaseService =
                new GemPurchaseService(
                        gemPackRepository,
                        gemPurchaseRepository,
                        verifier,
                        gemCreditService,
                        purchaseService);

        lenient().when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));
        lenient()
                .when(gemPurchaseRepository.save(any(GemPurchase.class)))
                .thenAnswer(
                        invocation -> {
                            GemPurchase p = invocation.getArgument(0);
                            p.setId(UUID.randomUUID());
                            return p;
                        });
        lenient()
                .when(gemPackRepository.findByProductIdAndActiveTrue(PRODUCT_ID))
                .thenReturn(Optional.of(pack));
        lenient()
                .when(gemPurchaseRepository.findByPurchaseToken(TOKEN))
                .thenReturn(Optional.empty());
    }

    private static User user(String email, String username) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .username(username)
                .name("Test User")
                .passwordHash("hashed")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    private static VerifiedProductPurchase verified(int purchaseState, int consumptionState) {
        return new VerifiedProductPurchase(
                purchaseState, consumptionState, "GPA.1234", Instant.parse("2026-09-18T10:00:00Z"));
    }

    private GemPurchase existingPurchase(User owner) {
        return GemPurchase.builder()
                .id(UUID.randomUUID())
                .user(owner)
                .gemPack(pack)
                .productId(PRODUCT_ID)
                .purchaseToken(TOKEN)
                .orderId("GPA.1234")
                .gemsGranted(300)
                .purchasedAt(Instant.now())
                .verifiedAt(Instant.now())
                .build();
    }

    @Test
    void shouldListActivePacksInOrder() {
        GemPack small =
                GemPack.builder()
                        .id(UUID.randomUUID())
                        .productId("gems_pack_120")
                        .gems(120)
                        .sortOrder(1)
                        .build();
        when(gemPackRepository.findByActiveTrueOrderBySortOrderAsc())
                .thenReturn(List.of(small, pack));

        List<GemPackResponse> packs = gemPurchaseService.getPacks();

        assertEquals(2, packs.size());
        assertEquals("gems_pack_120", packs.get(0).productId());
        assertEquals(120, packs.get(0).gems());
        assertEquals(PRODUCT_ID, packs.get(1).productId());
    }

    @Test
    void shouldCreditGemsAndRecordPurchase() {
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(verified(GooglePlayPurchaseVerifier.PURCHASE_STATE_PURCHASED, 0));

        GemPurchaseResponse response = gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN);

        assertFalse(response.alreadyGranted());
        assertEquals(300, response.gemsGranted());
        assertEquals(PRODUCT_ID, response.productId());
        assertEquals("GPA.1234", response.orderId());
        assertEquals(400, stats.getGems());
        assertEquals(400, response.inventory().gems());
        verify(userStatsRepository).save(stats);
        verify(gemPurchaseRepository).save(any(GemPurchase.class));
    }

    @Test
    void shouldCreateStatsWhenUserHasNone() {
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(verified(GooglePlayPurchaseVerifier.PURCHASE_STATE_PURCHASED, 0));
        when(userStatsRepository.save(any(UserStats.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        GemPurchaseResponse response = gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN);

        assertEquals(300, response.gemsGranted());
        verify(userStatsRepository).save(any(UserStats.class));
    }

    @Test
    void shouldReturnExistingPurchaseWhenSameUserRedeemsTokenAgain() {
        when(gemPurchaseRepository.findByPurchaseToken(TOKEN))
                .thenReturn(Optional.of(existingPurchase(user)));

        GemPurchaseResponse response = gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN);

        assertTrue(response.alreadyGranted());
        assertEquals(300, response.gemsGranted());
        assertEquals(100, stats.getGems());
        verify(verifier, never()).verify(any(), any());
        verify(userStatsRepository, never()).save(any(UserStats.class));
    }

    @Test
    void shouldRejectTokenRedeemedByAnotherUser() {
        User other = user("other@example.com", "other");
        when(gemPurchaseRepository.findByPurchaseToken(TOKEN))
                .thenReturn(Optional.of(existingPurchase(other)));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN));
        verify(verifier, never()).verify(any(), any());
    }

    @Test
    void shouldRejectUnknownPack() {
        when(gemPackRepository.findByProductIdAndActiveTrue("nope")).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> gemPurchaseService.redeem(user, "nope", TOKEN));
        verify(verifier, never()).verify(any(), any());
    }

    @Test
    void shouldRejectPendingPurchase() {
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(verified(GooglePlayPurchaseVerifier.PURCHASE_STATE_PENDING, 0));

        InvalidInputException ex =
                assertThrows(
                        InvalidInputException.class,
                        () -> gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN));

        assertEquals("Purchase is still pending", ex.getMessage());
        assertEquals(100, stats.getGems());
    }

    @Test
    void shouldRejectCanceledPurchase() {
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(verified(GooglePlayPurchaseVerifier.PURCHASE_STATE_CANCELED, 0));

        assertThrows(
                InvalidInputException.class,
                () -> gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN));
        verify(gemPurchaseRepository, never()).save(any(GemPurchase.class));
    }

    @Test
    void shouldRejectAlreadyConsumedToken() {
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(
                        verified(
                                GooglePlayPurchaseVerifier.PURCHASE_STATE_PURCHASED,
                                GooglePlayPurchaseVerifier.CONSUMPTION_STATE_CONSUMED));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN));
        verify(gemPurchaseRepository, never()).save(any(GemPurchase.class));
    }

    @Test
    void shouldResolveRaceOnDuplicateTokenByReturningWinner() {
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(verified(GooglePlayPurchaseVerifier.PURCHASE_STATE_PURCHASED, 0));
        when(gemPurchaseRepository.save(any(GemPurchase.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate purchase_token"));
        when(gemPurchaseRepository.findByPurchaseToken(TOKEN))
                .thenReturn(Optional.empty(), Optional.of(existingPurchase(user)));

        GemPurchaseResponse response = gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN);

        assertTrue(response.alreadyGranted());
    }

    @Test
    void shouldRethrowRaceWhenWinnerCannotBeFound() {
        when(verifier.verify(PRODUCT_ID, TOKEN))
                .thenReturn(verified(GooglePlayPurchaseVerifier.PURCHASE_STATE_PURCHASED, 0));
        when(gemPurchaseRepository.save(any(GemPurchase.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate purchase_token"));

        assertThrows(
                DataIntegrityViolationException.class,
                () -> gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN));
    }

    @Test
    void shouldRejectDisabledUser() {
        user.setEnabled(false);

        assertThrows(
                NotFoundException.class, () -> gemPurchaseService.redeem(user, PRODUCT_ID, TOKEN));
        verify(verifier, never()).verify(eq(PRODUCT_ID), eq(TOKEN));
    }
}
