package com.signasource.signa_api.users.service;

import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.users.dto.FriendEventResponse;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FriendEventService {

    private final FriendshipRepository friendshipRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;

    @Transactional(readOnly = true)
    public List<FriendEventResponse> getFriendsEvents(User user, int limit) {
        List<Friendship> friendships =
                friendshipRepository.findAllFriendshipsByUserAndStatus(
                        user, FriendshipStatus.ACCEPTED);

        List<FriendEventResponse> events = new ArrayList<>();

        for (Friendship friendship : friendships) {
            User friend =
                    friendship.getRequester().getId().equals(user.getId())
                            ? friendship.getAddressee()
                            : friendship.getRequester();

            events.addAll(getAchievementEvents(friend));
            events.addAll(getLearnedSignsEvents(friend));
        }

        return events.stream()
                .sorted(Comparator.comparing(FriendEventResponse::createdAt).reversed())
                .limit(limit)
                .toList();
    }

    private List<FriendEventResponse> getAchievementEvents(User friend) {
        var achievements =
                userAchievementRepository.findByUserOrderByEarnedAtDesc(
                        friend, PageRequest.of(0, 5));

        return achievements.stream()
                .map(
                        ua ->
                                new FriendEventResponse(
                                        friend.getId(),
                                        friend.getUsername(),
                                        friend.getName(),
                                        "ACHIEVEMENT",
                                        String.format(
                                                "Earned achievement: %s",
                                                ua.getAchievement().getTitle()),
                                        ua.getEarnedAt()))
                .toList();
    }

    private List<FriendEventResponse> getLearnedSignsEvents(User friend) {
        var learnedSigns =
                userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        friend, PageRequest.of(0, 5));

        return learnedSigns.stream()
                .map(
                        uls ->
                                new FriendEventResponse(
                                        friend.getId(),
                                        friend.getUsername(),
                                        friend.getName(),
                                        "SIGN_LEARNED",
                                        String.format(
                                                "Learned sign: %s (%s)",
                                                uls.getSign(),
                                                uls.getCourseVersion().getCourse().getName()),
                                        uls.getLearnedAt()))
                .toList();
    }
}
