package com.signasource.signa_api.users.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.auth.repository.TokenRepository;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.notification.repository.DeviceTokenRepository;
import com.signasource.signa_api.users.dto.RelationStatus;
import com.signasource.signa_api.users.dto.UpdateUsernameRequest;
import com.signasource.signa_api.users.dto.UserSearchResultResponse;
import com.signasource.signa_api.users.dto.UsernameAvailabilityResponse;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.FriendshipRepository;
import com.signasource.signa_api.users.repository.UserRepository;
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
class UserServiceTest {

    private static final String CURRENT_USERNAME = "currentuser";
    private static final String NEW_USERNAME = "newuser";
    private static final String TAKEN_USERNAME = "takenuser";

    @Mock private UserRepository userRepository;
    @Mock private TokenRepository tokenRepository;
    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private FriendshipRepository friendshipRepository;

    @InjectMocks private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user =
                User.builder()
                        .id(UUID.randomUUID())
                        .email("user@example.com")
                        .username(CURRENT_USERNAME)
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .accountVisibility(AccountVisibility.PUBLIC)
                        .build();
    }

    @Test
    void checkUsernameAvailability_whenUsernameIsFree_returnsAvailableTrue() {
        when(userRepository.existsByUsername(NEW_USERNAME)).thenReturn(false);

        UsernameAvailabilityResponse response = userService.checkUsernameAvailability(NEW_USERNAME);

        assertTrue(response.available());
    }

    @Test
    void checkUsernameAvailability_whenUsernameTaken_returnsAvailableFalse() {
        when(userRepository.existsByUsername(TAKEN_USERNAME)).thenReturn(true);

        UsernameAvailabilityResponse response =
                userService.checkUsernameAvailability(TAKEN_USERNAME);

        assertFalse(response.available());
    }

    @Test
    void updateUsername_whenNewUsernameIsFree_updatesAndSaves() {
        when(userRepository.existsByUsername(NEW_USERNAME)).thenReturn(false);

        userService.updateUsername(new UpdateUsernameRequest(NEW_USERNAME), user);

        assertEquals(NEW_USERNAME, user.getUsername());
        verify(userRepository).save(user);
    }

    @Test
    void updateUsername_whenSameAsCurrentUsername_doesNothing() {
        userService.updateUsername(new UpdateUsernameRequest(CURRENT_USERNAME), user);

        verify(userRepository, never()).existsByUsername(CURRENT_USERNAME);
        verify(userRepository, never()).save(user);
    }

    @Test
    void updateUsername_whenUsernameTaken_throwsConflict() {
        when(userRepository.existsByUsername(TAKEN_USERNAME)).thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> userService.updateUsername(new UpdateUsernameRequest(TAKEN_USERNAME), user));

        verify(userRepository, never()).save(user);
    }

    @Test
    void deleteAccount_disablesUserObfuscatesEmailAndRevokesTokens() {
        UUID userId = UUID.randomUUID();
        user =
                User.builder()
                        .id(userId)
                        .email("user@example.com")
                        .username(CURRENT_USERNAME)
                        .name("Test User")
                        .passwordHash("hashed")
                        .role(Role.USER)
                        .enabled(true)
                        .build();

        userService.deleteAccount(user);

        assertFalse(user.isEnabled());
        assertTrue(user.getEmail().startsWith("deleted_" + userId));
        verify(userRepository).save(user);
        verify(tokenRepository).deleteByUser(user);
        verify(deviceTokenRepository).deleteByUser(user);
    }

    private User user(String username, String name) {
        User u = new User();
        u.setId(UUID.randomUUID());
        u.setUsername(username);
        u.setName(name);
        u.setEnabled(true);
        return u;
    }

    private Friendship friendship(User requester, User addressee, FriendshipStatus status) {
        Friendship f = new Friendship();
        f.setRequester(requester);
        f.setAddressee(addressee);
        f.setStatus(status);
        return f;
    }

    @Test
    void searchUsers_ReturnsNoneForAStranger() {
        User me = user("me", "Me");
        User stranger = user("stranger", "Stranger");

        when(userRepository.searchByUsernameOrName(
                        eq("stran"), eq(me.getId()), any(Pageable.class)))
                .thenReturn(List.of(stranger));
        when(friendshipRepository.findAllByUser(me)).thenReturn(List.of());

        List<UserSearchResultResponse> results = userService.searchUsers(me, "stran", 20);

        assertEquals(1, results.size());
        assertEquals(RelationStatus.NONE, results.get(0).relation());
        assertEquals(0L, results.get(0).mutualFriends());
    }

    @Test
    void searchUsers_ResolvesEachRelationFromTheCallerPointOfView() {
        User me = user("me", "Me");
        User friend = user("friend", "Friend");
        User theyAsked = user("theyasked", "They Asked");
        User iAsked = user("iasked", "I Asked");
        User blockedByMe = user("blocked", "Blocked");

        when(userRepository.searchByUsernameOrName(any(), eq(me.getId()), any(Pageable.class)))
                .thenReturn(List.of(friend, theyAsked, iAsked, blockedByMe));
        when(friendshipRepository.findAllByUser(me))
                .thenReturn(
                        List.of(
                                friendship(me, friend, FriendshipStatus.ACCEPTED),
                                friendship(theyAsked, me, FriendshipStatus.PENDING),
                                friendship(me, iAsked, FriendshipStatus.PENDING),
                                friendship(me, blockedByMe, FriendshipStatus.BLOCKED)));
        when(friendshipRepository.countMutualFriends(any(), anyCollection())).thenReturn(0L);

        List<UserSearchResultResponse> results = userService.searchUsers(me, "a", 20);

        assertEquals(RelationStatus.FRIEND, results.get(0).relation());
        assertEquals(RelationStatus.INCOMING, results.get(1).relation());
        assertEquals(RelationStatus.OUTGOING, results.get(2).relation());
        assertEquals(RelationStatus.BLOCKED, results.get(3).relation());
    }

    /** Someone who blocked the caller must not surface in their search results. */
    @Test
    void searchUsers_HidesUsersWhoBlockedTheCaller() {
        User me = user("me", "Me");
        User blocker = user("blocker", "Blocker");

        when(userRepository.searchByUsernameOrName(any(), eq(me.getId()), any(Pageable.class)))
                .thenReturn(List.of(blocker));
        when(friendshipRepository.findAllByUser(me))
                .thenReturn(List.of(friendship(blocker, me, FriendshipStatus.BLOCKED)));

        assertTrue(userService.searchUsers(me, "block", 20).isEmpty());
    }

    /** A rejected request leaves no trace: the user can be added again. */
    @Test
    void searchUsers_TreatsARejectedRequestAsNoRelation() {
        User me = user("me", "Me");
        User rejected = user("rejected", "Rejected");

        when(userRepository.searchByUsernameOrName(any(), eq(me.getId()), any(Pageable.class)))
                .thenReturn(List.of(rejected));
        when(friendshipRepository.findAllByUser(me))
                .thenReturn(List.of(friendship(me, rejected, FriendshipStatus.REJECTED)));

        List<UserSearchResultResponse> results = userService.searchUsers(me, "rej", 20);

        assertEquals(RelationStatus.NONE, results.get(0).relation());
    }

    @Test
    void searchUsers_CountsMutualFriendsOnlyWhenTheCallerHasFriends() {
        User me = user("me", "Me");
        User friend = user("friend", "Friend");
        User candidate = user("candidate", "Candidate");

        when(userRepository.searchByUsernameOrName(any(), eq(me.getId()), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(friendshipRepository.findAllByUser(me))
                .thenReturn(List.of(friendship(me, friend, FriendshipStatus.ACCEPTED)));
        when(friendshipRepository.countMutualFriends(eq(candidate.getId()), anyCollection()))
                .thenReturn(3L);

        List<UserSearchResultResponse> results = userService.searchUsers(me, "cand", 20);

        assertEquals(3L, results.get(0).mutualFriends());
    }

    @Test
    void searchUsers_SkipsTheMutualQueryWhenThereAreNoMatches() {
        User me = user("me", "Me");

        when(userRepository.searchByUsernameOrName(any(), eq(me.getId()), any(Pageable.class)))
                .thenReturn(List.of());

        assertTrue(userService.searchUsers(me, "nobody", 20).isEmpty());
        verify(friendshipRepository, never()).findAllByUser(me);
    }
}
