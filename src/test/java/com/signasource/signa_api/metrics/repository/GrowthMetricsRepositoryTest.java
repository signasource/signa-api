package com.signasource.signa_api.metrics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.entity.GemPurchase;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
import com.signasource.signa_api.gamification.repository.GemPurchaseRepository;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.RefundStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.PurchaseRefundRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import com.signasource.signa_api.users.repository.UserDailyActivityRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import com.signasource.signa_api.waitlist.repository.WaitlistRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Runs the growth dashboard aggregates against H2 to check the JPQL, not just its syntax. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GrowthMetricsRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private GrowthMetricsRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private WaitlistRepository waitlistRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private LessonBlockAttemptRepository attemptRepository;
    @Autowired private CoursePurchaseRepository purchaseRepository;
    @Autowired private PurchaseSeatRepository seatRepository;
    @Autowired private PurchaseRefundRepository refundRepository;
    @Autowired private GemPackRepository gemPackRepository;
    @Autowired private GemPurchaseRepository gemPurchaseRepository;
    @Autowired private UserCourseEnrollmentRepository enrollmentRepository;
    @Autowired private UserDailyActivityRepository dailyActivityRepository;

    private static final Instant FROM = Instant.parse("2000-01-01T00:00:00Z");
    private final Instant until = Instant.now().plus(1, ChronoUnit.DAYS);

    private Course course;

    @BeforeEach
    void setUp() {
        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("G" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Global language")
                                .countryCode("AR")
                                .build());
        course =
                courseRepository.save(
                        Course.builder()
                                .code("growth-" + UUID.randomUUID())
                                .name("Growth course")
                                .signLanguage(language)
                                .build());
    }

    @Test
    void countsNewUsersAndActiveUsers() {
        User user = saveUser();
        attemptRepository.save(anAttempt(user));

        assertEquals(1, repository.countNewUsersBetween(FROM, until));
        assertEquals(1, repository.countUsersBefore(until));
        assertEquals(1, repository.dailyNewUsers(FROM, until).size());
        assertEquals(1, repository.countActiveUsersBetween(FROM, until));
        assertEquals(1, repository.dailyActiveUsers(FROM, until).size());
    }

    @Test
    void countsActivatedNewUsersAndReturnsCohortRows() {
        User activated = saveUser();
        attemptRepository.save(anAttempt(activated));
        dailyActivityRepository.save(
                UserDailyActivity.builder()
                        .user(activated)
                        .activityDate(java.time.LocalDate.now())
                        .minutes(5)
                        .build());
        saveUser(); // registered but never started learning

        assertEquals(1, repository.countActivatedNewUsersBetween(FROM, until));
        // One row per (user, activity day); the user without activity still appears once (left
        // join).
        assertEquals(2, repository.cohortActivityRows(FROM, until).size());
    }

    @Test
    void countsWaitlistSignups() {
        waitlistRepository.save(
                WaitlistEntry.builder()
                        .email(UUID.randomUUID() + "@mail.com")
                        .createdAt(Instant.now())
                        .build());

        assertEquals(1, repository.countWaitlistBefore(until));
        assertEquals(1, repository.countNewWaitlistBetween(FROM, until));
        assertEquals(1, repository.dailyWaitlist(FROM, until).size());
    }

    @Test
    void sumsConfirmedRevenueAndRefunds() {
        CoursePurchase purchase = savePaidPurchase(new BigDecimal("12000.00"));
        refundRepository.save(
                PurchaseRefund.builder()
                        .purchase(purchase)
                        .paymentId("mp-" + UUID.randomUUID().toString().substring(0, 20))
                        .amount(new BigDecimal("2000.00"))
                        .currency("ARS")
                        .reason(RefundReason.AMOUNT_MISMATCH)
                        .status(RefundStatus.REFUNDED)
                        .refundedAt(Instant.now())
                        .build());

        assertEquals(
                0,
                new BigDecimal("12000.00").compareTo(repository.sumPaidAmountBetween(FROM, until)));
        assertEquals(1, repository.countPaidBetween(FROM, until));
        assertEquals(1, repository.dailyRevenue(FROM, until).size());
        assertEquals(
                0, new BigDecimal("2000.00").compareTo(repository.sumRefundedBetween(FROM, until)));
    }

    @Test
    void countsSeatUtilization() {
        CoursePurchase purchase = savePaidPurchase(new BigDecimal("6000.00"));
        seatRepository.save(seat(purchase, SeatStatus.ACTIVE));
        seatRepository.save(seat(purchase, SeatStatus.INVITED));

        assertEquals(2, repository.countSeatsSoldBefore(until));
        assertEquals(1, repository.countSeatsActiveBefore(until));
    }

    @Test
    void sumsGemEconomyAndGroupsOrganizations() {
        User user = saveUser();
        GemPack pack =
                gemPackRepository.save(
                        GemPack.builder().productId("gems_" + UUID.randomUUID()).gems(500).build());
        gemPurchaseRepository.save(
                GemPurchase.builder()
                        .user(user)
                        .gemPack(pack)
                        .productId(pack.getProductId())
                        .purchaseToken("tok-" + UUID.randomUUID())
                        .gemsGranted(500)
                        .purchasedAt(Instant.now())
                        .verifiedAt(Instant.now())
                        .build());
        organizationRepository.save(
                Organization.builder()
                        .name("Org " + UUID.randomUUID())
                        .status(OrganizationStatus.ACTIVE)
                        .build());

        assertEquals(1, repository.countGemPurchasesBetween(FROM, until));
        assertEquals(500, repository.sumGemsGrantedBetween(FROM, until));
        assertEquals(1, repository.dailyGemPurchases(FROM, until).size());
        assertEquals(1, repository.organizationsByStatus(until).size());
        assertEquals(OrganizationStatus.ACTIVE, repository.organizationsByStatus(until).get(0)[0]);
    }

    @Test
    void countsUsersInPaidCourses() {
        // The course from setUp is not free (isFree defaults to false).
        CourseVersion version =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1.0.0")
                                .status(VersionStatus.PUBLISHED)
                                .course(course)
                                .build());
        enrollmentRepository.save(
                UserCourseEnrollment.builder()
                        .user(saveUser())
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .startedAt(Instant.now())
                        .build());

        assertEquals(1, repository.countPaidCourseUsers(until));
    }

    private CoursePurchase savePaidPurchase(BigDecimal total) {
        Organization org =
                organizationRepository.save(
                        Organization.builder()
                                .name("Buyer " + UUID.randomUUID())
                                .status(OrganizationStatus.ACTIVE)
                                .build());
        return purchaseRepository.save(
                CoursePurchase.builder()
                        .organization(org)
                        .course(course)
                        .seats(1)
                        .durationDays(30)
                        .signCount(10)
                        .listPricePerSeat(total)
                        .discountPercent(0)
                        .pricePerSeat(total)
                        .totalAmount(total)
                        .currency("ARS")
                        .status(PurchaseStatus.PAID)
                        .paidAt(Instant.now())
                        .build());
    }

    private PurchaseSeat seat(CoursePurchase purchase, SeatStatus status) {
        return PurchaseSeat.builder()
                .purchase(purchase)
                .email(UUID.randomUUID() + "@mail.com")
                .status(status)
                .build();
    }

    private LessonBlockAttempt anAttempt(User user) {
        CourseVersion version =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1.0.0")
                                .status(VersionStatus.PUBLISHED)
                                .course(course)
                                .build());
        Topic topic =
                topicRepository.save(
                        Topic.builder()
                                .code("t1")
                                .title("Greetings")
                                .order(1)
                                .courseVersion(version)
                                .build());
        Lesson lesson =
                lessonRepository.save(
                        Lesson.builder().code("l1").name("Hello").order(1).topic(topic).build());
        LessonBlock block =
                lessonBlockRepository.save(
                        LessonBlock.builder()
                                .type(BlockType.SELECT_MEANING)
                                .order(1)
                                .lesson(lesson)
                                .build());
        return LessonBlockAttempt.builder().user(user).lessonBlock(block).isCorrect(true).build();
    }

    private User saveUser() {
        return userRepository.save(
                User.builder()
                        .email(UUID.randomUUID() + "@mail.com")
                        .username("u" + UUID.randomUUID())
                        .passwordHash("hash")
                        .name("Tester")
                        .lastName("Growth")
                        .enabled(true)
                        .verified(true)
                        .build());
    }
}
