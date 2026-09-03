package com.signasource.signa_api.users.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.notification.entity.NotificationCode;
import com.signasource.signa_api.notification.service.NotificationService;
import com.signasource.signa_api.users.dto.FriendEventResponse;
import com.signasource.signa_api.users.entity.FriendEventLike;
import com.signasource.signa_api.users.entity.FriendEventType;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendEventLikeRepository;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds the friends activity feed. Events are not stored: they are derived from each friend's most
 * recent achievements and learned signs, and identified by ({@code eventType}, {@code eventRefId})
 * so a like can be attached to one.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FriendEventService {

    private static final int EVENTS_PER_FRIEND = 5;

    private final FriendshipRepository friendshipRepository;
    private final UserAchievementRepository userAchievementRepository;
    private final UserLearnedSignRepository userLearnedSignRepository;
    private final FriendEventLikeRepository friendEventLikeRepository;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public List<FriendEventResponse> getFriendsEvents(User user, int limit) {
        List<Friendship> friendships =
                friendshipRepository.findAllFriendshipsByUserAndStatus(
                        user, FriendshipStatus.ACCEPTED);

        List<FriendEventResponse> events = new ArrayList<>();

        for (Friendship friendship : friendships) {
            User friend = otherParty(friendship, user);
            events.addAll(getAchievementEvents(friend));
            events.addAll(getLearnedSignsEvents(friend));
        }

        List<FriendEventResponse> page =
                events.stream()
                        .sorted(Comparator.comparing(FriendEventResponse::createdAt).reversed())
                        .limit(limit)
                        .toList();

        return markLiked(user, page);
    }

    /**
     * Records a like on a friend's event and notifies its owner. Liking twice is a no-op, so no
     * duplicate notification is sent.
     */
    @Transactional
    public void likeEvent(User user, FriendEventType eventType, UUID eventRefId) {
        if (friendEventLikeRepository
                .findByUserAndEventTypeAndEventRefId(user, eventType, eventRefId)
                .isPresent()) {
            return;
        }

        User owner = resolveEventOwner(eventType, eventRefId);
        if (owner.getId().equals(user.getId())) {
            throw new InvalidInputException("You can't like your own activity");
        }
        if (!areFriends(user, owner)) {
            throw new InvalidInputException("You can only like the activity of your friends");
        }

        friendEventLikeRepository.save(
                FriendEventLike.builder()
                        .user(user)
                        .eventType(eventType)
                        .eventRefId(eventRefId)
                        .eventOwnerId(owner.getId())
                        .build());

        notifyLiked(owner, user);
    }

    @Transactional
    public void unlikeEvent(User user, FriendEventType eventType, UUID eventRefId) {
        friendEventLikeRepository
                .findByUserAndEventTypeAndEventRefId(user, eventType, eventRefId)
                .ifPresent(friendEventLikeRepository::delete);
    }

    private List<FriendEventResponse> markLiked(User user, List<FriendEventResponse> events) {
        if (events.isEmpty()) {
            return events;
        }

        Set<UUID> refIds =
                events.stream().map(FriendEventResponse::eventRefId).collect(Collectors.toSet());
        Set<String> liked =
                friendEventLikeRepository.findByUserAndEventRefIdIn(user, refIds).stream()
                        .map(l -> likeKey(l.getEventType(), l.getEventRefId()))
                        .collect(Collectors.toSet());

        return events.stream()
                .map(
                        e ->
                                liked.contains(likeKey(e.eventType(), e.eventRefId()))
                                        ? new FriendEventResponse(
                                                e.friendId(),
                                                e.friendUsername(),
                                                e.friendName(),
                                                e.eventType(),
                                                e.eventRefId(),
                                                e.subject(),
                                                e.context(),
                                                true,
                                                e.createdAt())
                                        : e)
                .toList();
    }

    private static String likeKey(FriendEventType type, UUID refId) {
        return type.name() + ":" + refId;
    }

    private User resolveEventOwner(FriendEventType eventType, UUID eventRefId) {
        return switch (eventType) {
            case ACHIEVEMENT ->
                    userAchievementRepository
                            .findById(eventRefId)
                            .orElseThrow(() -> new NotFoundException("Event not found"))
                            .getUser();
            case SIGN_LEARNED ->
                    userLearnedSignRepository
                            .findById(eventRefId)
                            .orElseThrow(() -> new NotFoundException("Event not found"))
                            .getUser();
        };
    }

    private boolean areFriends(User user, User other) {
        return friendshipRepository
                .findFriendshipBetween(user, other)
                .filter(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .isPresent();
    }

    /** A failed notification must not roll back the like itself. */
    private void notifyLiked(User owner, User liker) {
        try {
            notificationService.notifyUser(
                    owner.getId(),
                    NotificationCode.FRIEND_EVENT_LIKED,
                    Map.of(
                            "friend", liker.getName(),
                            "friendUsername", liker.getUsername(),
                            "friendId", liker.getId().toString()));
        } catch (RuntimeException ex) {
            log.warn("Could not notify user {} about a like", owner.getId(), ex);
        }
    }

    private static User otherParty(Friendship friendship, User user) {
        return friendship.getRequester().getId().equals(user.getId())
                ? friendship.getAddressee()
                : friendship.getRequester();
    }

    private List<FriendEventResponse> getAchievementEvents(User friend) {
        var achievements =
                userAchievementRepository.findByUserOrderByEarnedAtDesc(
                        friend, PageRequest.of(0, EVENTS_PER_FRIEND));

        return achievements.stream()
                .map(
                        ua ->
                                new FriendEventResponse(
                                        friend.getId(),
                                        friend.getUsername(),
                                        friend.getName(),
                                        FriendEventType.ACHIEVEMENT,
                                        ua.getId(),
                                        ua.getAchievement().getTitle(),
                                        ua.getAchievement().getDescription(),
                                        false,
                                        ua.getEarnedAt()))
                .toList();
    }

    private List<FriendEventResponse> getLearnedSignsEvents(User friend) {
        var learnedSigns =
                userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        friend, PageRequest.of(0, EVENTS_PER_FRIEND));

        return learnedSigns.stream()
                .map(
                        uls ->
                                new FriendEventResponse(
                                        friend.getId(),
                                        friend.getUsername(),
                                        friend.getName(),
                                        FriendEventType.SIGN_LEARNED,
                                        uls.getId(),
                                        uls.getSign(),
                                        uls.getCourseVersion().getCourse().getName(),
                                        false,
                                        uls.getLearnedAt()))
                .toList();
    }
}
