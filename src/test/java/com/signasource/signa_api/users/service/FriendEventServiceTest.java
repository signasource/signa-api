package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.UserAchievement;
import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.gamification.repository.UserAchievementRepository;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.users.dto.FriendEventResponse;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class FriendEventServiceTest {

    @Mock private FriendshipRepository friendshipRepository;

    @Mock private UserAchievementRepository userAchievementRepository;

    @Mock private UserLearnedSignRepository userLearnedSignRepository;

    @InjectMocks private FriendEventService friendEventService;

    private User currentUser;
    private User friend1;
    private UUID currentUserId;
    private UUID friend1Id;

    @BeforeEach
    void setUp() {
        currentUserId = UUID.randomUUID();
        friend1Id = UUID.randomUUID();

        currentUser = new User();
        currentUser.setId(currentUserId);
        currentUser.setUsername("currentUser");
        currentUser.setName("Current User");

        friend1 = new User();
        friend1.setId(friend1Id);
        friend1.setUsername("friend1");
        friend1.setName("Friend 1");
    }

    @Test
    void getFriendsEvents_ReturnsEventsFromFriendsAchievements() {
        Friendship friendship = new Friendship();
        friendship.setRequester(currentUser);
        friendship.setAddressee(friend1);
        friendship.setStatus(FriendshipStatus.ACCEPTED);

        Achievement achievement = new Achievement();
        achievement.setId(UUID.randomUUID());
        achievement.setTitle("First Achievement");

        UserAchievement userAchievement = new UserAchievement();
        userAchievement.setUser(friend1);
        userAchievement.setAchievement(achievement);
        userAchievement.setEarnedAt(Instant.now());

        when(friendshipRepository.findAllFriendshipsByUserAndStatus(
                        currentUser, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(friendship));

        when(userAchievementRepository.findByUserOrderByEarnedAtDesc(
                        eq(friend1), any(Pageable.class)))
                .thenReturn(List.of(userAchievement));

        when(userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        eq(friend1), any(Pageable.class)))
                .thenReturn(List.of());

        List<FriendEventResponse> events = friendEventService.getFriendsEvents(currentUser, 50);

        assertEquals(1, events.size());
        assertEquals("ACHIEVEMENT", events.get(0).eventType());
    }

    @Test
    void getFriendsEvents_ReturnsEventsFromFriendsLearnedSigns() {
        Friendship friendship = new Friendship();
        friendship.setRequester(currentUser);
        friendship.setAddressee(friend1);
        friendship.setStatus(FriendshipStatus.ACCEPTED);

        Course course = new Course();
        course.setName("Sign Language Course");

        CourseVersion courseVersion = new CourseVersion();
        courseVersion.setCourse(course);

        UserLearnedSign userLearnedSign = new UserLearnedSign();
        userLearnedSign.setUser(friend1);
        userLearnedSign.setSign("HELLO");
        userLearnedSign.setCourseVersion(courseVersion);
        userLearnedSign.setLearnedAt(Instant.now());

        when(friendshipRepository.findAllFriendshipsByUserAndStatus(
                        currentUser, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(friendship));

        when(userAchievementRepository.findByUserOrderByEarnedAtDesc(
                        eq(friend1), any(Pageable.class)))
                .thenReturn(List.of());

        when(userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        eq(friend1), any(Pageable.class)))
                .thenReturn(List.of(userLearnedSign));

        List<FriendEventResponse> events = friendEventService.getFriendsEvents(currentUser, 50);

        assertEquals(1, events.size());
        assertEquals("SIGN_LEARNED", events.get(0).eventType());
    }

    @Test
    void getFriendsEvents_ReturnsEmptyList_WhenNoFriends() {
        when(friendshipRepository.findAllFriendshipsByUserAndStatus(
                        currentUser, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of());

        List<FriendEventResponse> events = friendEventService.getFriendsEvents(currentUser, 50);

        assertEquals(0, events.size());
    }

    @Test
    void getFriendsEvents_LimitIsApplied() {
        Friendship friendship = new Friendship();
        friendship.setRequester(currentUser);
        friendship.setAddressee(friend1);
        friendship.setStatus(FriendshipStatus.ACCEPTED);

        Achievement achievement1 = new Achievement();
        achievement1.setTitle("Achievement 1");

        Achievement achievement2 = new Achievement();
        achievement2.setTitle("Achievement 2");

        UserAchievement userAchievement1 = new UserAchievement();
        userAchievement1.setUser(friend1);
        userAchievement1.setAchievement(achievement1);
        userAchievement1.setEarnedAt(Instant.now());

        UserAchievement userAchievement2 = new UserAchievement();
        userAchievement2.setUser(friend1);
        userAchievement2.setAchievement(achievement2);
        userAchievement2.setEarnedAt(Instant.now().minusSeconds(1000));

        when(friendshipRepository.findAllFriendshipsByUserAndStatus(
                        currentUser, FriendshipStatus.ACCEPTED))
                .thenReturn(List.of(friendship));

        when(userAchievementRepository.findByUserOrderByEarnedAtDesc(
                        eq(friend1), any(Pageable.class)))
                .thenReturn(List.of(userAchievement1, userAchievement2));

        when(userLearnedSignRepository.findByUserOrderByLearnedAtDesc(
                        eq(friend1), any(Pageable.class)))
                .thenReturn(List.of());

        List<FriendEventResponse> events = friendEventService.getFriendsEvents(currentUser, 1);

        assertEquals(1, events.size());
    }
}
