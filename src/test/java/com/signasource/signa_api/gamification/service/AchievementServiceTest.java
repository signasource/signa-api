package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.AchievementCriteriaType;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.AchievementRepository;
import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
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
class AchievementServiceTest {

    @Mock private AchievementRepository achievementRepository;
    @Mock private UserAchievementRepository userAchievementRepository;

    @InjectMocks private AchievementService achievementService;

    private User user;
    private Achievement earnedAchievement;
    private Achievement unearnedAchievement;
    private UserAchievement userAchievement;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("user@example.com")
                        .username("testuser")
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();

        earnedAchievement =
                Achievement.builder()
                        .id(UUID.randomUUID())
                        .code("STREAK_7")
                        .title("Racha de 7 días")
                        .description("Mantené una racha de 7 días")
                        .criteriaType(AchievementCriteriaType.STREAK_DAYS)
                        .criteriaValue(7)
                        .active(true)
                        .build();

        unearnedAchievement =
                Achievement.builder()
                        .id(UUID.randomUUID())
                        .code("TOTAL_XP_1000")
                        .title("1000 XP")
                        .description("Acumulá 1000 XP")
                        .criteriaType(AchievementCriteriaType.TOTAL_XP)
                        .criteriaValue(1000)
                        .active(false)
                        .build();

        userAchievement =
                UserAchievement.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .achievement(earnedAchievement)
                        .earnedAt(Instant.now())
                        .build();
    }

    @Test
    void getAchievements_whenUserEnabled_returnsAllWithEarnedFlagsSet() {
        when(achievementRepository.findAllWithUserAchievement(user))
                .thenReturn(
                        List.of(
                                new Object[] {earnedAchievement, userAchievement},
                                new Object[] {unearnedAchievement, null}));

        List<AchievementResponse> responses = achievementService.getAchievements(user, null, null);

        assertEquals(2, responses.size());

        AchievementResponse earnedResponse =
                responses.stream()
                        .filter(r -> r.id().equals(earnedAchievement.getId()))
                        .findFirst()
                        .orElseThrow();
        assertTrue(earnedResponse.earned());
        assertEquals(userAchievement.getEarnedAt(), earnedResponse.earnedAt());

        AchievementResponse unearnedResponse =
                responses.stream()
                        .filter(r -> r.id().equals(unearnedAchievement.getId()))
                        .findFirst()
                        .orElseThrow();
        assertFalse(unearnedResponse.earned());
        assertNull(unearnedResponse.earnedAt());
        assertFalse(unearnedResponse.active());
    }

    @Test
    void getAchievements_whenUserHasNoneEarned_returnsAllUnearned() {
        when(achievementRepository.findAllWithUserAchievement(user))
                .thenReturn(
                        List.of(
                                new Object[] {earnedAchievement, null},
                                new Object[] {unearnedAchievement, null}));

        List<AchievementResponse> responses = achievementService.getAchievements(user, null, null);

        assertTrue(responses.stream().noneMatch(AchievementResponse::earned));
    }

    @Test
    void getAchievements_whenFilteringByUnlocked_returnsOnlyEarned() {
        when(achievementRepository.findAllWithUserAchievement(user))
                .thenReturn(
                        List.of(
                                new Object[] {earnedAchievement, userAchievement},
                                new Object[] {unearnedAchievement, null}));

        List<AchievementResponse> responses = achievementService.getAchievements(user, true, null);

        assertEquals(1, responses.size());
        assertEquals(earnedAchievement.getId(), responses.get(0).id());
    }

    @Test
    void getAchievements_whenFilteringByActive_returnsOnlyActive() {
        when(achievementRepository.findAllWithUserAchievement(user))
                .thenReturn(
                        List.of(
                                new Object[] {earnedAchievement, userAchievement},
                                new Object[] {unearnedAchievement, null}));

        List<AchievementResponse> responses = achievementService.getAchievements(user, null, true);

        assertEquals(1, responses.size());
        assertEquals(earnedAchievement.getId(), responses.get(0).id());
    }

    @Test
    void getAchievements_whenUserDisabled_throwsNotFound() {
        user.setEnabled(false);

        assertThrows(
                NotFoundException.class,
                () -> achievementService.getAchievements(user, null, null));
    }

    @Test
    void getAchievementById_whenEarned_returnsResponseWithEarnedTrue() {
        UUID id = earnedAchievement.getId();
        when(achievementRepository.findByIdWithUserAchievement(id, user))
                .thenReturn(Optional.of(new Object[] {earnedAchievement, userAchievement}));

        AchievementResponse response = achievementService.getAchievementById(id, user);

        assertEquals(id, response.id());
        assertTrue(response.earned());
        assertEquals(userAchievement.getEarnedAt(), response.earnedAt());
    }

    @Test
    void getAchievementById_whenNotEarned_returnsResponseWithEarnedFalse() {
        UUID id = unearnedAchievement.getId();
        when(achievementRepository.findByIdWithUserAchievement(id, user))
                .thenReturn(Optional.of(new Object[] {unearnedAchievement, null}));

        AchievementResponse response = achievementService.getAchievementById(id, user);

        assertEquals(id, response.id());
        assertFalse(response.earned());
        assertNull(response.earnedAt());
    }

    @Test
    void getAchievementById_whenAchievementNotFound_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(achievementRepository.findByIdWithUserAchievement(id, user))
                .thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class, () -> achievementService.getAchievementById(id, user));
    }

    @Test
    void getAchievementById_whenUserDisabled_throwsNotFound() {
        user.setEnabled(false);
        UUID id = earnedAchievement.getId();

        assertThrows(
                NotFoundException.class, () -> achievementService.getAchievementById(id, user));
    }

    @Test
    void awardStreakMilestones_grantsReachedAchievementsAndCreditsShields() {
        Achievement streak3 =
                Achievement.builder()
                        .id(UUID.randomUUID())
                        .code("STREAK_3")
                        .criteriaType(AchievementCriteriaType.STREAK_DAYS)
                        .criteriaValue(3)
                        .rewardStreakShields(2)
                        .build();
        UserStats stats = UserStats.builder().user(user).currentStreak(3).streakShields(1).build();
        when(achievementRepository.findUnearnedReached(
                        user, AchievementCriteriaType.STREAK_DAYS, 3))
                .thenReturn(List.of(streak3));
        when(userAchievementRepository.save(any(UserAchievement.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<UserAchievement> granted = achievementService.awardStreakMilestones(user, stats);

        assertEquals(1, granted.size());
        assertEquals(streak3, granted.get(0).getAchievement());
        assertNull(granted.get(0).getSeenAt());
        assertEquals(3, stats.getStreakShields());
    }

    @Test
    void awardStreakMilestones_whenNothingReached_changesNothing() {
        UserStats stats = UserStats.builder().user(user).currentStreak(1).streakShields(1).build();
        when(achievementRepository.findUnearnedReached(
                        user, AchievementCriteriaType.STREAK_DAYS, 1))
                .thenReturn(List.of());

        assertTrue(achievementService.awardStreakMilestones(user, stats).isEmpty());

        assertEquals(1, stats.getStreakShields());
        verify(userAchievementRepository, never()).save(any());
    }

    @Test
    void getUnseen_returnsPendingEarnedAchievements() {
        when(userAchievementRepository.findByUserAndSeenAtIsNullOrderByEarnedAtAsc(user))
                .thenReturn(List.of(userAchievement));

        List<AchievementResponse> responses = achievementService.getUnseen(user);

        assertEquals(1, responses.size());
        assertTrue(responses.get(0).earned());
        assertEquals(earnedAchievement.getId(), responses.get(0).id());
    }

    @Test
    void markSeen_stampsSeenAtOnce() {
        when(userAchievementRepository.findByUserAndAchievementId(user, earnedAchievement.getId()))
                .thenReturn(Optional.of(userAchievement));

        achievementService.markSeen(earnedAchievement.getId(), user);

        assertTrue(userAchievement.getSeenAt() != null);
        verify(userAchievementRepository).save(userAchievement);
    }

    @Test
    void markSeen_whenNotEarned_throwsNotFound() {
        UUID id = UUID.randomUUID();
        when(userAchievementRepository.findByUserAndAchievementId(user, id))
                .thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> achievementService.markSeen(id, user));
    }
}
