package com.signasource.signa_api.users.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.InviteCode;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.InviteCodeRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.Friendship;
import com.signasource.signa_api.users.entity.FriendshipStatus;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
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
    @Autowired private CoursePurchaseRepository purchaseRepository;
    @Autowired private PurchaseSeatRepository seatRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;

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
    void shouldAnonymizeTheLeavingUsersSeatsButKeepThePurchase() {
        Organization org =
                organizationRepository.save(Organization.builder().name("Seats").build());
        CoursePurchase purchase = purchaseRepository.save(purchase(org));
        PurchaseSeat mine =
                seatRepository.save(
                        PurchaseSeat.builder()
                                .purchase(purchase)
                                .email(leaving.getEmail().toUpperCase())
                                .status(SeatStatus.INVITED)
                                .invitationToken("leaving-token")
                                .build());
        PurchaseSeat other =
                seatRepository.save(
                        PurchaseSeat.builder()
                                .purchase(purchase)
                                .email(staying.getEmail())
                                .status(SeatStatus.INVITED)
                                .invitationToken("staying-token")
                                .build());

        accountErasureRepository.deleteUserData(leaving);
        entityManager.flush();
        entityManager.clear();

        PurchaseSeat erased = seatRepository.findById(mine.getId()).orElseThrow();
        assertEquals("erased-" + mine.getId() + "@invalid", erased.getEmail());
        assertNull(erased.getInvitationToken());
        assertEquals(
                staying.getEmail(),
                seatRepository.findById(other.getId()).orElseThrow().getEmail());
        assertTrue(purchaseRepository.existsById(purchase.getId()));
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

    private CoursePurchase purchase(Organization org) {
        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("E" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Erasure language")
                                .countryCode("AR")
                                .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("erasure-" + UUID.randomUUID())
                                .name("Erasure course")
                                .signLanguage(language)
                                .build());
        return CoursePurchase.builder()
                .organization(org)
                .course(course)
                .seats(2)
                .durationDays(30)
                .signCount(1)
                .pricePerSeat(BigDecimal.ONE)
                .totalAmount(BigDecimal.TWO)
                .currency("ARS")
                .build();
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
