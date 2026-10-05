package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.ChallengeClaimResponse;
import com.signasource.signa_api.gamification.dto.ChallengeResponse;
import com.signasource.signa_api.gamification.dto.ChallengesResponse;
import com.signasource.signa_api.gamification.entity.Challenge;
import com.signasource.signa_api.gamification.entity.ChallengeCriteriaType;
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
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * First steps and daily challenges.
 *
 * <p>Each user gets one {@link UserChallenge} row per challenge: the first-steps rows live forever
 * under {@link #FIRST_STEPS_PERIOD}, the daily ones are created per {@link ArgentinaTime} day. Rows
 * are created lazily, on the first read or progress report of the period. Progress only touches
 * those rows; the reward (mostly gems) is credited when the user claims it.
 */
@Service
@RequiredArgsConstructor
public class ChallengeService {

    /** Period key of the first-steps rows, which never roll over. */
    static final LocalDate FIRST_STEPS_PERIOD = LocalDate.EPOCH;

    /** Exercises driven by the camera; the same set counts in lessons and in free practice. */
    private static final Set<BlockType> CAMERA_BLOCKS =
            EnumSet.of(BlockType.VISUAL_RECOGNITION, BlockType.PERFORM_SIGN, BlockType.SPELL_NAME);

    private final ChallengeRepository challengeRepository;
    private final UserChallengeRepository userChallengeRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserLessonProgressRepository lessonProgressRepository;
    private final LessonBlockAttemptRepository lessonBlockAttemptRepository;
    private final PracticeAttemptRepository practiceAttemptRepository;
    private final FriendshipRepository friendshipRepository;

    public static boolean isCameraBlock(BlockType type) {
        return CAMERA_BLOCKS.contains(type);
    }

    @Transactional
    public ChallengesResponse getChallenges(User user) {
        ensureEnabled(user);

        List<UserChallenge> rows = ensureRows(user);
        List<ChallengeResponse> firstSteps = responsesOf(rows, ChallengeType.FIRST_STEPS);
        boolean firstStepsDone =
                !firstSteps.isEmpty() && firstSteps.stream().allMatch(c -> c.rewardClaimed());

        return new ChallengesResponse(
                firstSteps,
                firstStepsDone,
                firstStepsDone ? responsesOf(rows, ChallengeType.DAILY) : List.of(),
                ArgentinaTime.today().plusDays(1).atStartOfDay(ArgentinaTime.ZONE).toInstant());
    }

    /**
     * Reports progress on every active challenge of {@code type}. For absolute criteria {@code
     * value} is the new total (progress only moves up); otherwise it is an amount to add. Does not
     * touch the user's stats.
     */
    @Transactional
    public void record(User user, ChallengeCriteriaType type, long value) {
        if (value <= 0) {
            return;
        }
        List<UserChallenge> changed = new ArrayList<>();
        for (UserChallenge row : ensureRows(user)) {
            if (row.getChallenge().getCriteriaType() != type || row.isCompleted()) {
                continue;
            }
            long next =
                    type.isAbsolute()
                            ? Math.max(row.getCurrentProgress(), value)
                            : row.getCurrentProgress() + value;
            if (applyProgress(row, next)) {
                changed.add(row);
            }
        }
        userChallengeRepository.saveAll(changed);
    }

    @Transactional
    public ChallengeClaimResponse claim(UUID userChallengeId, User user) {
        ensureEnabled(user);

        UserChallenge row =
                userChallengeRepository
                        .findByIdAndUser(userChallengeId, user)
                        .orElseThrow(() -> new NotFoundException("Challenge not found"));
        if (!row.isCompleted()) {
            throw new InvalidInputException("Challenge is not completed yet");
        }
        if (row.getRewardClaimedAt() != null) {
            throw new InvalidInputException("Reward already claimed");
        }

        UserStats stats =
                userStatsRepository
                        .findByUser(user)
                        .orElseGet(
                                () ->
                                        UserStats.builder()
                                                .user(user)
                                                .updatedAt(Instant.now())
                                                .build());
        grantReward(stats, row.getChallenge());
        stats.setUpdatedAt(Instant.now());
        userStatsRepository.save(stats);

        row.setRewardClaimedAt(Instant.now());
        userChallengeRepository.save(row);
        return new ChallengeClaimResponse(ChallengeResponse.from(row), stats.getGems());
    }

    private void grantReward(UserStats stats, Challenge challenge) {
        int quantity = challenge.getRewardQuantity();
        switch (challenge.getRewardType()) {
            case GEMS -> stats.setGems(stats.getGems() + quantity);
            case STREAK_SHIELD -> stats.setStreakShields(stats.getStreakShields() + quantity);
            case LIFE -> {
                int current =
                        stats.getCurrentLives() == null
                                ? UserStats.MAX_LIVES
                                : stats.getCurrentLives();
                int updated = Math.min(UserStats.MAX_LIVES, current + quantity);
                stats.setCurrentLives(updated);
                if (updated >= UserStats.MAX_LIVES) {
                    stats.setNextLifeAt(null);
                }
            }
            case XP_MULTIPLIER -> {
                stats.setXpMultiplier(challenge.getRewardMultiplierValue());
                stats.setXpMultiplierExpiresAt(
                        Instant.now()
                                .plus(Duration.ofMinutes(challenge.getRewardDurationMinutes())));
            }
        }
    }

    /**
     * Returns the user's rows for the first steps and today, creating the ones that are missing.
     */
    private List<UserChallenge> ensureRows(User user) {
        LocalDate today = ArgentinaTime.today();
        Map<UUID, UserChallenge> existing = new HashMap<>();
        userChallengeRepository
                .findByUserAndPeriodStartIn(user, List.of(FIRST_STEPS_PERIOD, today))
                .forEach(row -> existing.put(row.getChallenge().getId(), row));

        List<UserChallenge> rows = new ArrayList<>();
        UserStats stats = null;
        for (Challenge challenge :
                challengeRepository.findByActiveTrueAndChallengeTypeIn(
                        List.of(ChallengeType.FIRST_STEPS, ChallengeType.DAILY))) {
            UserChallenge row = existing.get(challenge.getId());
            if (row == null) {
                if (stats == null) {
                    stats = userStatsRepository.findByUser(user).orElse(null);
                }
                row = userChallengeRepository.save(newRow(user, challenge, today, stats));
            }
            rows.add(row);
        }
        return rows;
    }

    private UserChallenge newRow(User user, Challenge challenge, LocalDate today, UserStats stats) {
        boolean firstSteps = challenge.getChallengeType() == ChallengeType.FIRST_STEPS;
        LocalDate period = firstSteps ? FIRST_STEPS_PERIOD : today;
        UserChallenge row =
                UserChallenge.builder()
                        .user(user)
                        .challenge(challenge)
                        .periodStart(period)
                        .periodEnd(period)
                        .startedAt(Instant.now())
                        .build();
        if (firstSteps) {
            // Users who were active before this existed get credit for what they already did.
            applyProgress(row, historicalProgress(user, challenge.getCriteriaType(), stats));
        }
        return row;
    }

    private long historicalProgress(User user, ChallengeCriteriaType type, UserStats stats) {
        return switch (type) {
            case COMPLETE_LESSONS ->
                    lessonProgressRepository.countByUserIdAndStatus(
                            user.getId(), ProgressStatus.COMPLETED);
            case CAMERA_PRACTICES ->
                    lessonBlockAttemptRepository.countByUserIdAndIsCorrectTrueAndLessonBlockTypeIn(
                                    user.getId(), CAMERA_BLOCKS)
                            + practiceAttemptRepository
                                    .countByUserIdAndIsCorrectTrueAndLessonBlockTypeIn(
                                            user.getId(), CAMERA_BLOCKS);
            case STREAK_DAYS -> stats == null ? 0 : stats.getCurrentStreak();
            case FRIEND_REQUESTS_SENT ->
                    Math.max(
                            friendshipRepository.countByRequester(user),
                            friendshipRepository.countAcceptedFriends(user));
            case EARN_XP, PERFECT_LESSONS -> 0;
        };
    }

    /** Sets the (capped) progress and completes the row when it reaches its target. */
    private boolean applyProgress(UserChallenge row, long progress) {
        int target = row.getChallenge().getCriteriaValue();
        int capped = (int) Math.min(progress, target);
        if (capped == row.getCurrentProgress()) {
            return false;
        }
        row.setCurrentProgress(capped);
        if (capped >= target) {
            row.setCompleted(true);
            row.setCompletedAt(Instant.now());
        }
        return true;
    }

    private List<ChallengeResponse> responsesOf(List<UserChallenge> rows, ChallengeType type) {
        return rows.stream()
                .filter(row -> row.getChallenge().getChallengeType() == type)
                .sorted(
                        Comparator.comparingInt(
                                (UserChallenge row) -> row.getChallenge().getDisplayOrder()))
                .map(ChallengeResponse::from)
                .toList();
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
