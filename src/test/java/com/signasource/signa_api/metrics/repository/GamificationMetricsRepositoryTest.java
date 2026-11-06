package com.signasource.signa_api.metrics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.AchievementCriteriaType;
import com.signasource.signa_api.gamification.entity.Challenge;
import com.signasource.signa_api.gamification.entity.ChallengeCriteriaType;
import com.signasource.signa_api.gamification.entity.ChallengeRewardType;
import com.signasource.signa_api.gamification.entity.ChallengeType;
import com.signasource.signa_api.gamification.entity.Gift;
import com.signasource.signa_api.gamification.entity.GiftStatus;
import com.signasource.signa_api.gamification.entity.Purchase;
import com.signasource.signa_api.gamification.entity.ShopItem;
import com.signasource.signa_api.gamification.entity.ShopItemType;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.gamification.entity.UserChallenge;
import com.signasource.signa_api.gamification.entity.UserDailyXp;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.AchievementRepository;
import com.signasource.signa_api.gamification.repository.ChallengeRepository;
import com.signasource.signa_api.gamification.repository.GiftRepository;
import com.signasource.signa_api.gamification.repository.PurchaseRepository;
import com.signasource.signa_api.gamification.repository.ShopItemRepository;
import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.gamification.repository.UserChallengeRepository;
import com.signasource.signa_api.gamification.repository.UserDailyXpRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Runs the gamification dashboard aggregates against H2 to check the JPQL, not just its syntax. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GamificationMetricsRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private GamificationMetricsRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserDailyXpRepository dailyXpRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private AchievementRepository achievementRepository;
    @Autowired private UserAchievementRepository userAchievementRepository;
    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private UserChallengeRepository userChallengeRepository;
    @Autowired private ShopItemRepository shopItemRepository;
    @Autowired private GiftRepository giftRepository;
    @Autowired private PurchaseRepository purchaseRepository;

    private static final Instant FROM = Instant.parse("2000-01-01T00:00:00Z");
    private static final LocalDate FROM_DATE = LocalDate.of(2000, 1, 1);
    private final Instant until = Instant.now().plus(1, ChronoUnit.DAYS);
    private final LocalDate toDate = LocalDate.now().plusDays(1);

    private User user;

    @BeforeEach
    void setUp() {
        user = saveUser();
    }

    @Test
    void sumsAndGroupsDailyXp() {
        dailyXpRepository.save(
                UserDailyXp.builder().user(user).xpDate(LocalDate.now()).xpEarned(250).build());

        assertEquals(250L, repository.sumXpBetween(FROM_DATE, toDate));
        assertEquals(1, repository.dailyXp(FROM_DATE, toDate).size());
    }

    @Test
    void summarisesStreaks() {
        userStatsRepository.save(
                UserStats.builder().user(user).currentStreak(10).updatedAt(Instant.now()).build());

        assertEquals(1, repository.countActiveStreakers());
        assertEquals(1, repository.countStreakOver(7));
        assertTrue(repository.averageCurrentStreak() >= 10.0);
        // No attempt in the window → the IN-subquery is empty → AVG is null (exercises the JPQL).
        assertNull(repository.averageStreakActive(FROM, until));
    }

    @Test
    void countsAchievementUnlocks() {
        Achievement achievement =
                achievementRepository.save(
                        Achievement.builder()
                                .code("ach-" + UUID.randomUUID())
                                .title("Racha 10")
                                .criteriaType(AchievementCriteriaType.STREAK_DAYS)
                                .criteriaValue(10)
                                .build());
        userAchievementRepository.save(
                UserAchievement.builder()
                        .user(user)
                        .achievement(achievement)
                        .earnedAt(Instant.now())
                        .build());

        assertEquals(1, repository.countUnlockedBetween(FROM, until));
        assertEquals(1, repository.dailyUnlocked(FROM, until).size());
        assertEquals(1, repository.topUnlockedAchievements().size());
        assertEquals("Racha 10", repository.topUnlockedAchievements().get(0)[0]);
    }

    @Test
    void computesChallengeClaimRates() {
        Challenge challenge =
                challengeRepository.save(
                        Challenge.builder()
                                .code("ch-" + UUID.randomUUID())
                                .title("Diario")
                                .challengeType(ChallengeType.DAILY)
                                .criteriaType(ChallengeCriteriaType.EARN_XP)
                                .criteriaValue(50)
                                .rewardType(ChallengeRewardType.GEMS)
                                .rewardQuantity(10)
                                .build());
        userChallengeRepository.save(
                UserChallenge.builder()
                        .user(user)
                        .challenge(challenge)
                        .completed(true)
                        .completedAt(Instant.now())
                        .rewardClaimedAt(Instant.now())
                        .periodStart(LocalDate.now())
                        .periodEnd(LocalDate.now())
                        .startedAt(Instant.now())
                        .build());

        var rows = repository.challengeClaims(FROM, until);
        assertEquals(1, rows.size());
        assertEquals(ChallengeType.DAILY, rows.get(0)[0]);
        assertEquals(1L, ((Number) rows.get(0)[1]).longValue());
        assertEquals(1L, ((Number) rows.get(0)[2]).longValue());
    }

    @Test
    void countsGiftsAndShopPurchases() {
        User recipient = saveUser();
        ShopItem item =
                shopItemRepository.save(
                        ShopItem.builder()
                                .code("item-" + UUID.randomUUID())
                                .title("Escudo")
                                .itemType(ShopItemType.STREAK_SHIELD)
                                .priceGems(100)
                                .build());
        Gift gift =
                Gift.builder()
                        .sender(user)
                        .recipient(recipient)
                        .shopItem(item)
                        .status(GiftStatus.CLAIMED)
                        .sentAt(Instant.now())
                        .claimedAt(Instant.now())
                        .build();
        giftRepository.save(gift);
        purchaseRepository.save(
                Purchase.builder()
                        .user(user)
                        .shopItem(item)
                        .gemsSpent(100)
                        .purchasedAt(Instant.now())
                        .build());

        assertEquals(1, repository.countGiftsSentBetween(FROM, until));
        assertEquals(1, repository.countGiftsClaimedBetween(FROM, until));
        assertEquals(100L, repository.sumGemsSpentBetween(FROM, until));
        assertEquals(1, repository.dailyGemsSpent(FROM, until).size());
        assertEquals(1, repository.topShopItems(FROM, until).size());
        assertEquals("Escudo", repository.topShopItems(FROM, until).get(0)[0]);
    }

    private User saveUser() {
        return userRepository.save(
                User.builder()
                        .email(UUID.randomUUID() + "@mail.com")
                        .username("u" + UUID.randomUUID())
                        .passwordHash("hash")
                        .name("Tester")
                        .lastName("Gamification")
                        .enabled(true)
                        .verified(true)
                        .build());
    }
}
