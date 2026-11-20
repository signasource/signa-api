package com.signasource.signa_api.users.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.users.entity.AccountVisibility;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Private accounts stay out of user search and the global ranking. Runs the JPQL against H2. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountVisibilityQueriesTest {

    private static final int TOP_XP = 9_000_000;

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private UserRepository userRepository;
    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private UserStatsRepository userStatsRepository;

    private String token;
    private User searcher;
    private User publicUser;
    private User privateStranger;
    private User privateFriend;

    @BeforeEach
    void setUp() {
        token = "vis" + UUID.randomUUID().toString().substring(0, 8);
        searcher = saveUser("searcher", AccountVisibility.PUBLIC);
        publicUser = saveUser(token + "pub", AccountVisibility.PUBLIC);
        privateStranger = saveUser(token + "priv", AccountVisibility.PRIVATE);
        privateFriend = saveUser(token + "privfriend", AccountVisibility.PRIVATE);
        friendshipRepository.save(
                Friendship.builder()
                        .requester(privateFriend)
                        .addressee(searcher)
                        .status(FriendshipStatus.ACCEPTED)
                        .build());
    }

    @Test
    void searchShouldHidePrivateAccountsUnlessAlreadyFriends() {
        List<UUID> ids =
                userRepository
                        .searchByUsernameOrName(token, searcher.getId(), PageRequest.of(0, 20))
                        .stream()
                        .map(User::getId)
                        .toList();

        assertTrue(ids.contains(publicUser.getId()));
        assertTrue(ids.contains(privateFriend.getId()));
        assertFalse(ids.contains(privateStranger.getId()));
    }

    @Test
    void searchShouldFindAPrivateAccountByItsExactUsernameIgnoringCase() {
        String exact = privateStranger.getUsername().toUpperCase();

        List<UUID> ids =
                userRepository
                        .searchByUsernameOrName(exact, searcher.getId(), PageRequest.of(0, 20))
                        .stream()
                        .map(User::getId)
                        .toList();

        assertTrue(ids.contains(privateStranger.getId()));
        // A partial match on the same private account still finds nothing.
        String partial = privateStranger.getUsername().substring(0, token.length() + 3);
        assertFalse(
                userRepository
                        .searchByUsernameOrName(partial, searcher.getId(), PageRequest.of(0, 20))
                        .stream()
                        .map(User::getId)
                        .toList()
                        .contains(privateStranger.getId()));
    }

    @Test
    void searchShouldHidePrivateAccountsWhoseRequestIsOnlyPending() {
        User requester = saveUser(token + "pending", AccountVisibility.PRIVATE);
        friendshipRepository.save(
                Friendship.builder()
                        .requester(requester)
                        .addressee(searcher)
                        .status(FriendshipStatus.PENDING)
                        .build());

        List<UUID> ids =
                userRepository
                        .searchByUsernameOrName(token, searcher.getId(), PageRequest.of(0, 20))
                        .stream()
                        .map(User::getId)
                        .toList();

        assertFalse(ids.contains(requester.getId()));
    }

    @Test
    void globalRankingShouldLeaveOutPrivateAccounts() {
        userStatsRepository.save(stats(publicUser, TOP_XP));
        userStatsRepository.save(stats(privateStranger, TOP_XP + 1));
        userStatsRepository.save(stats(privateFriend, TOP_XP + 2));

        List<UUID> top =
                userStatsRepository.findTopByWeeklyXpDesc(PageRequest.of(0, 100)).stream()
                        .map(s -> s.getUser().getId())
                        .toList();

        assertTrue(top.contains(publicUser.getId()));
        assertFalse(top.contains(privateStranger.getId()));
        assertFalse(top.contains(privateFriend.getId()));
        assertEquals(0, userStatsRepository.countUsersAheadByWeeklyXp(TOP_XP, publicUser.getId()));
    }

    @Test
    void globalRankingShouldLeaveOutDisabledAccounts() {
        User disabled = saveUser(token + "off", AccountVisibility.PUBLIC);
        disabled.setEnabled(false);
        userRepository.save(disabled);
        userStatsRepository.save(stats(publicUser, TOP_XP));
        userStatsRepository.save(stats(disabled, TOP_XP + 1));

        List<UUID> top =
                userStatsRepository.findTopByWeeklyXpDesc(PageRequest.of(0, 100)).stream()
                        .map(s -> s.getUser().getId())
                        .toList();

        assertFalse(top.contains(disabled.getId()));
        assertEquals(0, userStatsRepository.countUsersAheadByWeeklyXp(TOP_XP, publicUser.getId()));
    }

    private User saveUser(String username, AccountVisibility visibility) {
        return userRepository.save(
                User.builder()
                        .email(username + UUID.randomUUID() + "@mail.com")
                        .username(username)
                        .passwordHash("hash")
                        .name(username)
                        .lastName("Tester")
                        .role(Role.USER)
                        .enabled(true)
                        .verified(true)
                        .accountVisibility(visibility)
                        .build());
    }

    private UserStats stats(User user, int weeklyXp) {
        return UserStats.builder().user(user).weeklyXp(weeklyXp).updatedAt(Instant.now()).build();
    }
}
