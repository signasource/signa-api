package com.signasource.signa_api.gamification.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.ChallengeClaimResponse;
import com.signasource.signa_api.gamification.dto.ChallengesResponse;
import com.signasource.signa_api.gamification.entity.Challenge;
import com.signasource.signa_api.gamification.entity.ChallengeCriteriaType;
import com.signasource.signa_api.gamification.entity.ChallengeRewardType;
import com.signasource.signa_api.gamification.entity.ChallengeType;
import com.signasource.signa_api.gamification.entity.UserChallenge;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.ChallengeRepository;
import com.signasource.signa_api.gamification.repository.UserChallengeRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.PracticeAttemptRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
class ChallengeServiceTest {

    @Mock private ChallengeRepository challengeRepository;
    @Mock private UserChallengeRepository userChallengeRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private UserLessonProgressRepository lessonProgressRepository;
    @Mock private LessonBlockAttemptRepository lessonBlockAttemptRepository;
    @Mock private PracticeAttemptRepository practiceAttemptRepository;
    @Mock private FriendshipRepository friendshipRepository;

    @InjectMocks private ChallengeService challengeService;

    private User user;
    private LocalDate today;

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
        today = ArgentinaTime.today();
    }

    private Challenge challenge(
            ChallengeType type,
            ChallengeCriteriaType criteria,
            int target,
            ChallengeRewardType reward,
            int quantity) {
        return Challenge.builder()
                .id(UUID.randomUUID())
                .code(criteria + "_" + target)
                .title("t")
                .challengeType(type)
                .criteriaType(criteria)
                .criteriaValue(target)
                .rewardType(reward)
                .rewardQuantity(quantity)
                .build();
    }

    private UserChallenge row(
            Challenge challenge, int progress, boolean completed, boolean claimed) {
        LocalDate period =
                challenge.getChallengeType() == ChallengeType.FIRST_STEPS
                        ? ChallengeService.FIRST_STEPS_PERIOD
                        : today;
        return UserChallenge.builder()
                .id(UUID.randomUUID())
                .user(user)
                .challenge(challenge)
                .periodStart(period)
                .periodEnd(period)
                .currentProgress(progress)
                .completed(completed)
                .rewardClaimedAt(claimed ? Instant.now() : null)
                .startedAt(Instant.now())
                .build();
    }

    private void catalog(Challenge... challenges) {
        when(challengeRepository.findByActiveTrueAndChallengeTypeIn(anyCollection()))
                .thenReturn(List.of(challenges));
    }

    private void existingRows(UserChallenge... rows) {
        when(userChallengeRepository.findByUserAndPeriodStartIn(eq(user), anyCollection()))
                .thenReturn(List.of(rows));
    }

    // ── getChallenges ────────────────────────────────────────────────────

    @Test
    void getChallenges_HidesDailyUntilEveryFirstStepIsClaimed() {
        Challenge step =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        1,
                        ChallengeRewardType.GEMS,
                        20);
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        catalog(step, daily);
        existingRows(row(step, 1, true, false), row(daily, 0, false, false));

        ChallengesResponse response = challengeService.getChallenges(user);

        assertEquals(1, response.firstSteps().size());
        assertFalse(response.firstStepsDone());
        assertTrue(response.daily().isEmpty());
        assertNotNull(response.resetsAt());
    }

    @Test
    void getChallenges_ShowsDailyOnceFirstStepsAreClaimed() {
        Challenge step =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        1,
                        ChallengeRewardType.GEMS,
                        20);
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        catalog(step, daily);
        existingRows(row(step, 1, true, true), row(daily, 100, false, false));

        ChallengesResponse response = challengeService.getChallenges(user);

        assertTrue(response.firstStepsDone());
        assertEquals(1, response.daily().size());
        assertEquals(100, response.daily().get(0).progress());
    }

    @Test
    void getChallenges_SortsByDisplayOrder() {
        Challenge second =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.FRIEND_REQUESTS_SENT,
                        1,
                        ChallengeRewardType.GEMS,
                        30);
        second.setDisplayOrder(2);
        Challenge first =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        1,
                        ChallengeRewardType.GEMS,
                        20);
        first.setDisplayOrder(1);
        catalog(second, first);
        existingRows(row(second, 0, false, false), row(first, 0, false, false));

        ChallengesResponse response = challengeService.getChallenges(user);

        assertEquals(first.getCode(), response.firstSteps().get(0).code());
    }

    @Test
    void getChallenges_CreatesMissingRowsAndCreditsPastActivityOnFirstSteps() {
        Challenge lesson =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        1,
                        ChallengeRewardType.GEMS,
                        20);
        Challenge camera =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.CAMERA_PRACTICES,
                        1,
                        ChallengeRewardType.GEMS,
                        20);
        Challenge friend =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.FRIEND_REQUESTS_SENT,
                        1,
                        ChallengeRewardType.GEMS,
                        30);
        Challenge streak =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.STREAK_DAYS,
                        2,
                        ChallengeRewardType.GEMS,
                        30);
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        3,
                        ChallengeRewardType.GEMS,
                        50);
        catalog(lesson, camera, friend, streak, daily);
        existingRows();
        when(userStatsRepository.findByUser(user))
                .thenReturn(Optional.of(UserStats.builder().user(user).currentStreak(1).build()));
        when(lessonProgressRepository.countByUserIdAndStatus(
                        user.getId(), ProgressStatus.COMPLETED))
                .thenReturn(4L);
        when(lessonBlockAttemptRepository.countByUserIdAndIsCorrectTrueAndLessonBlockTypeIn(
                        eq(user.getId()), anyCollection()))
                .thenReturn(0L);
        when(practiceAttemptRepository.countByUserIdAndIsCorrectTrueAndLessonBlockTypeIn(
                        eq(user.getId()), anyCollection()))
                .thenReturn(0L);
        when(friendshipRepository.countByRequester(user)).thenReturn(2L);
        when(friendshipRepository.countAcceptedFriends(user)).thenReturn(0L);
        when(userChallengeRepository.save(any(UserChallenge.class)))
                .thenAnswer(i -> i.getArgument(0));

        ChallengesResponse response = challengeService.getChallenges(user);

        var byCode = response.firstSteps();
        assertTrue(
                byCode.stream()
                        .filter(c -> c.code().equals(lesson.getCode()))
                        .findFirst()
                        .orElseThrow()
                        .completed());
        assertFalse(
                byCode.stream()
                        .filter(c -> c.code().equals(camera.getCode()))
                        .findFirst()
                        .orElseThrow()
                        .completed());
        assertTrue(
                byCode.stream()
                        .filter(c -> c.code().equals(friend.getCode()))
                        .findFirst()
                        .orElseThrow()
                        .completed());
        assertEquals(
                1,
                byCode.stream()
                        .filter(c -> c.code().equals(streak.getCode()))
                        .findFirst()
                        .orElseThrow()
                        .progress());
        verify(userChallengeRepository, org.mockito.Mockito.times(5))
                .save(any(UserChallenge.class));
    }

    @Test
    void getChallenges_DailyRowsStartAtZero() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        3,
                        ChallengeRewardType.GEMS,
                        50);
        catalog(daily);
        existingRows();
        when(userChallengeRepository.save(any(UserChallenge.class)))
                .thenAnswer(i -> i.getArgument(0));

        challengeService.getChallenges(user);

        verify(lessonProgressRepository, never()).countByUserIdAndStatus(any(), any());
        verify(userChallengeRepository)
                .save(
                        org.mockito.ArgumentMatchers.argThat(
                                r ->
                                        r.getCurrentProgress() == 0
                                                && r.getPeriodStart().equals(today)));
    }

    @Test
    void getChallenges_DisabledUserIsNotFound() {
        user.setEnabled(false);
        assertThrows(NotFoundException.class, () -> challengeService.getChallenges(user));
    }

    // ── record ───────────────────────────────────────────────────────────

    @Test
    void record_AddsIncrementalProgressAndCompletesAtTarget() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        3,
                        ChallengeRewardType.GEMS,
                        50);
        UserChallenge existing = row(daily, 2, false, false);
        catalog(daily);
        existingRows(existing);

        challengeService.record(user, ChallengeCriteriaType.COMPLETE_LESSONS, 1);

        assertEquals(3, existing.getCurrentProgress());
        assertTrue(existing.isCompleted());
        assertNotNull(existing.getCompletedAt());
        verify(userChallengeRepository).saveAll(List.of(existing));
    }

    @Test
    void record_CapsProgressAtTarget() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        UserChallenge existing = row(daily, 480, false, false);
        catalog(daily);
        existingRows(existing);

        challengeService.record(user, ChallengeCriteriaType.EARN_XP, 100);

        assertEquals(500, existing.getCurrentProgress());
    }

    @Test
    void record_AbsoluteCriteriaTakesTheNewTotalAndNeverGoesDown() {
        Challenge streak =
                challenge(
                        ChallengeType.FIRST_STEPS,
                        ChallengeCriteriaType.STREAK_DAYS,
                        5,
                        ChallengeRewardType.GEMS,
                        30);
        UserChallenge existing = row(streak, 3, false, false);
        catalog(streak);
        existingRows(existing);

        challengeService.record(user, ChallengeCriteriaType.STREAK_DAYS, 2);
        assertEquals(3, existing.getCurrentProgress());

        challengeService.record(user, ChallengeCriteriaType.STREAK_DAYS, 4);
        assertEquals(4, existing.getCurrentProgress());
    }

    @Test
    void record_IgnoresOtherCriteriaAndCompletedRows() {
        Challenge xp =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        Challenge lessons =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.COMPLETE_LESSONS,
                        3,
                        ChallengeRewardType.GEMS,
                        50);
        UserChallenge done = row(lessons, 3, true, false);
        UserChallenge other = row(xp, 10, false, false);
        catalog(xp, lessons);
        existingRows(other, done);

        challengeService.record(user, ChallengeCriteriaType.COMPLETE_LESSONS, 1);

        assertEquals(10, other.getCurrentProgress());
        assertEquals(3, done.getCurrentProgress());
        verify(userChallengeRepository).saveAll(List.of());
    }

    @Test
    void record_NonPositiveValueDoesNothing() {
        challengeService.record(user, ChallengeCriteriaType.EARN_XP, 0);

        verify(challengeRepository, never()).findByActiveTrueAndChallengeTypeIn(anyCollection());
    }

    // ── claim ────────────────────────────────────────────────────────────

    @Test
    void claim_CreditsGemsAndStampsTheClaim() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        UserChallenge completed = row(daily, 500, true, false);
        UserStats stats = UserStats.builder().user(user).gems(10).build();
        when(userChallengeRepository.findByIdAndUser(completed.getId(), user))
                .thenReturn(Optional.of(completed));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        ChallengeClaimResponse response = challengeService.claim(completed.getId(), user);

        assertEquals(70, stats.getGems());
        assertEquals(70, response.gems());
        assertNotNull(completed.getRewardClaimedAt());
        assertTrue(response.challenge().rewardClaimed());
        verify(userStatsRepository).save(stats);
        verify(userChallengeRepository).save(completed);
    }

    @Test
    void claim_AppliesXpMultiplier() {
        Challenge perfect =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.PERFECT_LESSONS,
                        3,
                        ChallengeRewardType.XP_MULTIPLIER,
                        1);
        perfect.setRewardDurationMinutes(30);
        perfect.setRewardMultiplierValue(3.0);
        UserChallenge completed = row(perfect, 3, true, false);
        UserStats stats = UserStats.builder().user(user).build();
        when(userChallengeRepository.findByIdAndUser(completed.getId(), user))
                .thenReturn(Optional.of(completed));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        challengeService.claim(completed.getId(), user);

        assertEquals(3.0, stats.getEffectiveXpMultiplier());
    }

    @Test
    void claim_AppliesShieldAndLifeRewards() {
        Challenge shield =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        1,
                        ChallengeRewardType.STREAK_SHIELD,
                        2);
        Challenge life =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        2,
                        ChallengeRewardType.LIFE,
                        3);
        UserChallenge shieldRow = row(shield, 1, true, false);
        UserChallenge lifeRow = row(life, 2, true, false);
        UserStats stats =
                UserStats.builder()
                        .user(user)
                        .streakShields(1)
                        .currentLives(1)
                        .nextLifeAt(Instant.now())
                        .build();
        when(userChallengeRepository.findByIdAndUser(shieldRow.getId(), user))
                .thenReturn(Optional.of(shieldRow));
        when(userChallengeRepository.findByIdAndUser(lifeRow.getId(), user))
                .thenReturn(Optional.of(lifeRow));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        challengeService.claim(shieldRow.getId(), user);
        challengeService.claim(lifeRow.getId(), user);

        assertEquals(3, stats.getStreakShields());
        assertEquals(4, stats.getCurrentLives());
    }

    @Test
    void claim_LifeRewardClearsTheRegenTimerWhenFull() {
        Challenge life =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        2,
                        ChallengeRewardType.LIFE,
                        5);
        UserChallenge lifeRow = row(life, 2, true, false);
        UserStats stats =
                UserStats.builder().user(user).currentLives(1).nextLifeAt(Instant.now()).build();
        when(userChallengeRepository.findByIdAndUser(lifeRow.getId(), user))
                .thenReturn(Optional.of(lifeRow));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.of(stats));

        challengeService.claim(lifeRow.getId(), user);

        assertEquals(UserStats.MAX_LIVES, stats.getCurrentLives());
        assertEquals(null, stats.getNextLifeAt());
    }

    @Test
    void claim_CreatesStatsWhenTheUserHasNone() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        UserChallenge completed = row(daily, 500, true, false);
        when(userChallengeRepository.findByIdAndUser(completed.getId(), user))
                .thenReturn(Optional.of(completed));
        when(userStatsRepository.findByUser(user)).thenReturn(Optional.empty());

        ChallengeClaimResponse response = challengeService.claim(completed.getId(), user);

        assertEquals(60, response.gems());
    }

    @Test
    void claim_RejectsAnIncompleteChallenge() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        UserChallenge pending = row(daily, 10, false, false);
        when(userChallengeRepository.findByIdAndUser(pending.getId(), user))
                .thenReturn(Optional.of(pending));

        assertThrows(
                InvalidInputException.class, () -> challengeService.claim(pending.getId(), user));
        verify(userStatsRepository, never()).save(any());
    }

    @Test
    void claim_RejectsADoubleClaim() {
        Challenge daily =
                challenge(
                        ChallengeType.DAILY,
                        ChallengeCriteriaType.EARN_XP,
                        500,
                        ChallengeRewardType.GEMS,
                        60);
        UserChallenge claimed = row(daily, 500, true, true);
        when(userChallengeRepository.findByIdAndUser(claimed.getId(), user))
                .thenReturn(Optional.of(claimed));

        assertThrows(
                InvalidInputException.class, () -> challengeService.claim(claimed.getId(), user));
        verify(userStatsRepository, never()).save(any());
    }

    @Test
    void claim_UnknownOrForeignChallengeIsNotFound() {
        UUID id = UUID.randomUUID();
        when(userChallengeRepository.findByIdAndUser(id, user)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> challengeService.claim(id, user));
    }

    @Test
    void isCameraBlock_CoversCameraDrivenTypesOnly() {
        assertTrue(ChallengeService.isCameraBlock(BlockType.VISUAL_RECOGNITION));
        assertTrue(ChallengeService.isCameraBlock(BlockType.PERFORM_SIGN));
        assertTrue(ChallengeService.isCameraBlock(BlockType.SPELL_NAME));
        assertFalse(ChallengeService.isCameraBlock(BlockType.MATCH));
        assertFalse(
                new ArrayList<>(List.of(BlockType.INFO))
                        .stream().anyMatch(ChallengeService::isCameraBlock));
    }
}
