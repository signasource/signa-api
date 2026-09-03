package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.gamification.service.AchievementService;
import com.signasource.signa_api.gamification.service.UserStatsService;
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.users.dto.PublicUserProfileResponse;
import com.signasource.signa_api.users.dto.PublicUserStatsResponse;
import com.signasource.signa_api.users.dto.RelationStatus;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSettingsRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles another user's profile for the read-only profile screen, composing the pieces owned by
 * the gamification and learning modules.
 *
 * <p>Deliberately excludes gems, lives and boosters: those are the viewer's own wallet, not public
 * progress.
 */
@Service
@RequiredArgsConstructor
public class PublicProfileService {

    private static final String DEFAULT_HEADER_COLOR = "#FFFFFF";

    private final UserRepository userRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final UserStatsRepository userStatsRepository;
    private final UserStatsService userStatsService;
    private final AchievementService achievementService;
    private final CourseTrackingService courseTrackingService;
    private final FriendshipRepository friendshipRepository;

    @Transactional(readOnly = true)
    public PublicUserProfileResponse getByUsername(String username, User viewer) {
        User target =
                userRepository
                        .findByUsername(username)
                        .filter(User::isEnabled)
                        .orElseThrow(() -> new NotFoundException("User not found"));

        RelationStatus relation = relationBetween(viewer, target);

        if (!canView(target, viewer, relation)) {
            return PublicUserProfileResponse.hidden(target, relation);
        }

        return new PublicUserProfileResponse(
                target.getId(),
                target.getUsername(),
                target.getName(),
                headerColorOf(target),
                relation,
                true,
                PublicUserStatsResponse.from(
                        userStatsRepository.findByUserId(target.getId()).orElse(null)),
                userStatsService.getWeeklyXpBreakdown(target),
                achievementService.getAchievements(target, null, true),
                courseTrackingService.getUserCourseProgress(target));
    }

    /** A private account only opens up to the owner and to accepted friends. */
    private boolean canView(User target, User viewer, RelationStatus relation) {
        if (target.getAccountVisibility() == AccountVisibility.PUBLIC) {
            return true;
        }
        if (viewer == null) {
            return false;
        }
        return target.getId().equals(viewer.getId()) || relation == RelationStatus.FRIEND;
    }

    private String headerColorOf(User target) {
        return userSettingsRepository
                .findByUserId(target.getId())
                .map(
                        s ->
                                s.getProfileHeaderColor() != null
                                        ? s.getProfileHeaderColor()
                                        : DEFAULT_HEADER_COLOR)
                .orElse(DEFAULT_HEADER_COLOR);
    }

    private RelationStatus relationBetween(User viewer, User target) {
        if (viewer == null || viewer.getId().equals(target.getId())) {
            return RelationStatus.NONE;
        }

        Optional<Friendship> relation = friendshipRepository.findFriendshipBetween(viewer, target);
        if (relation.isEmpty()) {
            return RelationStatus.NONE;
        }

        Friendship friendship = relation.get();
        boolean viewerRequested = friendship.getRequester().getId().equals(viewer.getId());

        return switch (friendship.getStatus()) {
            case ACCEPTED -> RelationStatus.FRIEND;
            case PENDING -> viewerRequested ? RelationStatus.OUTGOING : RelationStatus.INCOMING;
            case BLOCKED -> viewerRequested ? RelationStatus.BLOCKED : RelationStatus.BLOCKED_BY;
            case REJECTED -> RelationStatus.NONE;
        };
    }
}
