package com.signasource.signa_api.gamification.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.AchievementCriteriaType;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.AchievementRepository;
import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AchievementService {

    private final AchievementRepository achievementRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserStatsRepository userStatsRepository;

    @Transactional(readOnly = true)
    public List<AchievementResponse> getAchievements(User user, Boolean unlocked, Boolean active) {
        ensureEnabled(user);

        return achievementRepository.findAllWithUserAchievement(user).stream()
                .map(
                        row ->
                                AchievementResponse.from(
                                        (Achievement) row[0], (UserAchievement) row[1]))
                .filter(r -> unlocked == null || r.earned() == unlocked)
                .filter(r -> active == null || r.active() == active)
                .toList();
    }

    @Transactional(readOnly = true)
    public AchievementResponse getAchievementById(UUID achievementId, User user) {
        ensureEnabled(user);

        Object[] row =
                achievementRepository
                        .findByIdWithUserAchievement(achievementId, user)
                        .orElseThrow(() -> new NotFoundException("Achievement not found"));

        return AchievementResponse.from((Achievement) row[0], (UserAchievement) row[1]);
    }

    /**
     * Grants every active achievement of {@code type} whose threshold {@code value} has reached and
     * credits its rewards (gems, streak shields) on {@code stats}. Does not save {@code stats}: the
     * caller owns that transaction.
     *
     * <p>Every grant starts unseen: the client celebrates it and then calls {@link #markSeen}.
     */
    @Transactional
    public List<UserAchievement> awardReached(
            User user, AchievementCriteriaType type, long value, UserStats stats) {
        List<Achievement> reached = achievementRepository.findUnearnedReached(user, type, value);
        Instant now = Instant.now();
        return reached.stream()
                .map(
                        achievement -> {
                            stats.setStreakShields(
                                    stats.getStreakShields()
                                            + achievement.getRewardStreakShields());
                            stats.setGems(stats.getGems() + achievement.getRewardGems());
                            return userAchievementRepository.save(
                                    UserAchievement.builder()
                                            .user(user)
                                            .achievement(achievement)
                                            .earnedAt(now)
                                            .build());
                        })
                .toList();
    }

    /** Same as above for callers that don't hold the user's stats; loads and saves them. */
    @Transactional
    public List<UserAchievement> awardReached(User user, AchievementCriteriaType type, long value) {
        UserStats stats =
                userStatsRepository
                        .findByUserId(user.getId())
                        .orElseGet(() -> UserStats.builder().user(user).build());
        List<UserAchievement> granted = awardReached(user, type, value, stats);
        if (!granted.isEmpty()) {
            stats.setUpdatedAt(Instant.now());
            userStatsRepository.save(stats);
        }
        return granted;
    }

    /** Earned achievements whose celebration the client has not shown yet, oldest first. */
    @Transactional(readOnly = true)
    public List<AchievementResponse> getUnseen(User user) {
        ensureEnabled(user);

        return userAchievementRepository.findByUserAndSeenAtIsNullOrderByEarnedAtAsc(user).stream()
                .map(ua -> AchievementResponse.from(ua.getAchievement(), ua))
                .toList();
    }

    @Transactional
    public void markSeen(UUID achievementId, User user) {
        ensureEnabled(user);

        UserAchievement earned =
                userAchievementRepository
                        .findByUserAndAchievementId(user, achievementId)
                        .orElseThrow(() -> new NotFoundException("Achievement not found"));
        if (earned.getSeenAt() == null) {
            earned.setSeenAt(Instant.now());
            userAchievementRepository.save(earned);
        }
    }

    private void ensureEnabled(User user) {
        if (!user.isEnabled()) {
            throw new NotFoundException("User not found");
        }
    }
}
