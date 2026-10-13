package com.signasource.signa_api.users.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.repository.InviteCodeRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Runs the bulk deletes against H2 so a typo in the JPQL fails here, not on a real delete. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class AccountErasureRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private AccountErasureRepository accountErasureRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private UserDailyActivityRepository dailyActivityRepository;
    @Autowired private UserStatsRepository userStatsRepository;
    @Autowired private InviteCodeRepository inviteCodeRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private EntityManager entityManager;

    private User leaving;
    private User staying;

    @BeforeEach
    void setUp() {
        leaving = saveUser("leaving");
        staying = saveUser("staying");
    }

    @Test
    void shouldDeleteOnlyTheLeavingUsersDataAcrossTables() {
        friendshipRepository.save(friendship(leaving, staying));
        dailyActivityRepository.save(activity(leaving));
        dailyActivityRepository.save(activity(staying));
        userStatsRepository.save(stats(leaving));
        userStatsRepository.save(stats(staying));

        accountErasureRepository.deleteUserData(leaving);
        entityManager.flush();
        entityManager.clear();

        assertEquals(0, friendshipRepository.findAllByUser(leaving).size());
        assertEquals(0, count("UserDailyActivity", leaving));
        assertEquals(0, count("UserStats", leaving));
        assertEquals(1, count("UserDailyActivity", staying));
        assertEquals(1, count("UserStats", staying));
        assertTrue(userRepository.existsById(leaving.getId()));
    }

    @Test
    void shouldDeleteInviteCodesBoundToTheLeavingUsersEmailOnly() {
        Organization org = organizationRepository.save(Organization.builder().name("Acme").build());
        inviteCodeRepository.save(code(org, "MINE1", leaving.getEmail().toUpperCase()));
        inviteCodeRepository.save(code(org, "OTHER1", staying.getEmail()));
        inviteCodeRepository.save(code(org, "SHARED1", null));

        accountErasureRepository.deleteUserData(leaving);
        entityManager.flush();

        assertFalse(inviteCodeRepository.existsByCode("MINE1"));
        assertTrue(inviteCodeRepository.existsByCode("OTHER1"));
        assertTrue(inviteCodeRepository.existsByCode("SHARED1"));
    }

    @Test
    void shouldRunWithoutErrorsWhenTheUserHasNoData() {
        accountErasureRepository.deleteUserData(leaving);
        entityManager.flush();

        assertTrue(userRepository.existsById(leaving.getId()));
    }

    private long count(String entity, User user) {
        return entityManager
                .createQuery(
                        "select count(e) from " + entity + " e where e.user = :user", Long.class)
                .setParameter("user", user)
                .getSingleResult();
    }

    private User saveUser(String username) {
        return userRepository.save(
                User.builder()
                        .email(username + UUID.randomUUID() + "@mail.com")
                        .username(username + UUID.randomUUID())
                        .passwordHash("hash")
                        .name(username)
                        .lastName("Tester")
                        .enabled(true)
                        .verified(true)
                        .build());
    }

    private InviteCode code(Organization org, String value, String email) {
        return InviteCode.builder().code(value).organization(org).email(email).build();
    }

    private Friendship friendship(User requester, User addressee) {
        return Friendship.builder()
                .requester(requester)
                .addressee(addressee)
                .status(FriendshipStatus.ACCEPTED)
                .build();
    }

    private UserDailyActivity activity(User user) {
        return UserDailyActivity.builder()
                .user(user)
                .activityDate(LocalDate.now())
                .minutes(5)
                .build();
    }

    private UserStats stats(User user) {
        return UserStats.builder().user(user).currentStreak(2).updatedAt(Instant.now()).build();
    }
}
