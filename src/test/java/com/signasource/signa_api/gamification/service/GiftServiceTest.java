package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.dto.AppliedEffectResponse;
import com.signasource.signa_api.gamification.dto.GiftClaimResponse;
import com.signasource.signa_api.gamification.dto.GiftResponse;
import com.signasource.signa_api.gamification.entity.Gift;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.GiftRepository;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class GiftServiceTest {

    @Mock private GiftRepository giftRepository;
    @Mock private PurchaseRepository purchaseRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private FriendshipRepository friendshipRepository;
    @Mock private UserRepository userRepository;
    @Mock private PurchaseService purchaseService;

    @InjectMocks private GiftService giftService;

    private User sender;
    private User recipient;
    private ShopItem item;

    @BeforeEach
    void setUp() {
        sender = user("sender@example.com", "sender");
        recipient = user("recipient@example.com", "recipient");
        item =
                ShopItem.builder()
                        .id(UUID.randomUUID())
                        .code("streak_shield_x1")
                        .title("Escudo de racha x1")
                        .itemType(ShopItemType.STREAK_SHIELD)
                        .priceGems(40)
                        .quantity(1)
                        .active(true)
                        .build();
    }

    private User user(String email, String username) {
        return User.builder()
                .id(UUID.randomUUID())
                .email(email)
                .username(username)
                .name(username)
                .passwordHash("hashed")
                .role(Role.USER)
                .enabled(true)
                .build();
    }

    private Friendship acceptedFriendship() {
        return Friendship.builder()
                .id(1L)
                .requester(sender)
                .addressee(recipient)
                .status(FriendshipStatus.ACCEPTED)
                .build();
    }

    @Test
    void sendGift_whenRecipientIsSelf_throwsInvalidInput() {
        assertThrows(
                InvalidInputException.class,
                () -> giftService.sendGift(sender, item.getId(), sender.getId(), "hi"));
    }

    @Test
    void sendGift_whenRecipientNotFound_throwsNotFound() {
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> giftService.sendGift(sender, item.getId(), recipient.getId(), "hi"));
    }

    @Test
    void sendGift_whenNotFriends_throwsInvalidInput() {
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(friendshipRepository.findFriendshipBetween(sender, recipient))
                .thenReturn(Optional.empty());

        assertThrows(
                InvalidInputException.class,
                () -> giftService.sendGift(sender, item.getId(), recipient.getId(), "hi"));
    }

    @Test
    void sendGift_whenFriendsAndValid_createsGift() {
        UserStats senderStats = UserStats.builder().user(sender).gems(100).build();
        when(userRepository.findById(recipient.getId())).thenReturn(Optional.of(recipient));
        when(friendshipRepository.findFriendshipBetween(sender, recipient))
                .thenReturn(Optional.of(acceptedFriendship()));
        when(purchaseService.getActiveItem(item.getId())).thenReturn(item);
        when(purchaseService.getOrCreateStats(sender)).thenReturn(senderStats);
        when(giftRepository.save(any(Gift.class)))
                .thenAnswer(
                        invocation -> {
                            Gift g = invocation.getArgument(0);
                            g.setId(UUID.randomUUID());
                            return g;
                        });

        GiftResponse response = giftService.sendGift(sender, item.getId(), recipient.getId(), "hi");

        verify(purchaseService).debitGems(senderStats, item.getPriceGems());
        assertEquals(GiftStatus.PENDING, response.status());
        assertEquals(recipient.getId(), response.recipientId());
    }

    @Test
    void claimGift_whenGiftNotFoundForRecipient_throwsNotFound() {
        UUID giftId = UUID.randomUUID();
        when(giftRepository.findByIdAndRecipient(giftId, recipient)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> giftService.claimGift(recipient, giftId));
    }

    @Test
    void claimGift_whenAlreadyClaimed_throwsResourceAlreadyInUse() {
        Gift gift = giftEntity(GiftStatus.CLAIMED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(giftRepository.findByIdAndRecipient(gift.getId(), recipient))
                .thenReturn(Optional.of(gift));

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> giftService.claimGift(recipient, gift.getId()));
    }

    @Test
    void claimGift_whenExpired_marksExpiredAndThrows() {
        Gift gift = giftEntity(GiftStatus.PENDING, Instant.now().minus(1, ChronoUnit.DAYS));
        when(giftRepository.findByIdAndRecipient(gift.getId(), recipient))
                .thenReturn(Optional.of(gift));

        assertThrows(
                InvalidInputException.class, () -> giftService.claimGift(recipient, gift.getId()));

        assertEquals(GiftStatus.EXPIRED, gift.getStatus());
        verify(giftRepository).save(gift);
        verify(purchaseService, never()).applyItemEffect(any(), any());
    }

    @Test
    void claimGift_whenPendingAndValid_appliesEffectAndMarksClaimed() {
        Gift gift = giftEntity(GiftStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        UserStats recipientStats = UserStats.builder().user(recipient).gems(0).build();
        when(giftRepository.findByIdAndRecipient(gift.getId(), recipient))
                .thenReturn(Optional.of(gift));
        when(purchaseService.getOrCreateStats(recipient)).thenReturn(recipientStats);
        when(purchaseService.applyItemEffect(recipientStats, item))
                .thenReturn(
                        new AppliedEffectResponse(
                                ShopItemType.STREAK_SHIELD, null, null, 1, null, null));

        GiftClaimResponse response = giftService.claimGift(recipient, gift.getId());

        assertEquals(GiftStatus.CLAIMED, gift.getStatus());
        assertEquals(1, response.effect().streakShieldsGranted());
        verify(giftRepository).save(gift);
    }

    @Test
    void getReceivedGifts_whenStatusFilterNull_returnsAllGifts() {
        Gift gift = giftEntity(GiftStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        when(giftRepository.findByRecipientOrderBySentAtDesc(recipient)).thenReturn(List.of(gift));

        List<GiftResponse> result = giftService.getReceivedGifts(recipient, null);

        assertEquals(1, result.size());
    }

    @Test
    void getReceivedGifts_whenStatusFilterProvided_returnsFilteredGifts() {
        Gift gift = giftEntity(GiftStatus.CLAIMED, Instant.now().plus(1, ChronoUnit.DAYS));
        when(giftRepository.findByRecipientAndStatusOrderBySentAtDesc(
                        recipient, GiftStatus.CLAIMED))
                .thenReturn(List.of(gift));

        List<GiftResponse> result = giftService.getReceivedGifts(recipient, GiftStatus.CLAIMED);

        assertEquals(1, result.size());
    }

    @Test
    void getSentGifts_returnsGiftsSentByUser() {
        Gift gift = giftEntity(GiftStatus.PENDING, Instant.now().plus(1, ChronoUnit.DAYS));
        when(giftRepository.findBySenderOrderBySentAtDesc(sender)).thenReturn(List.of(gift));

        List<GiftResponse> result = giftService.getSentGifts(sender);

        assertEquals(1, result.size());
    }

    private Gift giftEntity(GiftStatus status, Instant expiresAt) {
        return Gift.builder()
                .id(UUID.randomUUID())
                .sender(sender)
                .recipient(recipient)
                .shopItem(item)
                .message("hi")
                .status(status)
                .sentAt(Instant.now())
                .expiresAt(expiresAt)
                .build();
    }
}
