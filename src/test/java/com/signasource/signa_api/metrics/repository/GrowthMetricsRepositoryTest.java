package com.signasource.signa_api.metrics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.GemOrder;
import com.signasource.signa_api.gamification.entity.GemOrderStatus;
import com.signasource.signa_api.gamification.entity.GemPack;
import com.signasource.signa_api.gamification.repository.GemOrderRepository;
import com.signasource.signa_api.gamification.repository.GemPackRepository;
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
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.entity.OrganizationStatus;
import com.signasource.signa_api.organizations.entity.PurchaseRefund;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.RefundStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMemberRepository;
import com.signasource.signa_api.organizations.repository.OrganizationRepository;
import com.signasource.signa_api.organizations.repository.PurchaseRefundRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserActivity;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import com.signasource.signa_api.users.entity.UserSession;
import com.signasource.signa_api.users.repository.UserActivityRepository;
import com.signasource.signa_api.users.repository.UserDailyActivityRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.repository.UserSessionRepository;
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
    @Autowired private OrganizationMemberRepository organizationMemberRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private LessonBlockAttemptRepository attemptRepository;
    @Autowired private UserActivityRepository activityRepository;
    @Autowired private CoursePurchaseRepository purchaseRepository;
    @Autowired private PurchaseSeatRepository seatRepository;
    @Autowired private PurchaseRefundRepository refundRepository;
    @Autowired private GemPackRepository gemPackRepository;
    @Autowired private GemOrderRepository gemOrderRepository;
    @Autowired private UserCourseEnrollmentRepository enrollmentRepository;
    @Autowired private UserDailyActivityRepository dailyActivityRepository;
    @Autowired private UserSessionRepository userSessionRepository;

    private static final Instant FROM = Instant.parse("2000-01-01T00:00:00Z");
    private static final long BUCKET = 86400L; // day resolution for the wide test window
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
        // Any activity counts (not just exercises); several touches are still one active user.
        activityRepository.save(
                UserActivity.builder().user(user).occurredAt(Instant.now()).build());
        activityRepository.save(
                UserActivity.builder().user(user).occurredAt(Instant.now()).build());

        assertEquals(1, repository.countNewUsersBetween(FROM, until));
        assertEquals(1, repository.countUsersBefore(until));
        assertEquals(1, repository.dailyNewUsers(FROM, until, BUCKET).size());
        assertEquals(1, repository.countActiveUsersBetween(FROM, until));
        assertEquals(1, repository.dailyActiveUsers(FROM, until, BUCKET).size());
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
    void aggregatesSessions() {
        User user = saveUser();
        User other = saveUser();
        userSessionRepository.save(session(user, 120));
        userSessionRepository.save(session(user, 240));
        userSessionRepository.save(session(other, 60));

        assertEquals(3, repository.countSessionsBetween(FROM, until));
        assertEquals(2, repository.countUsersWithSessionBetween(FROM, until));
        assertEquals(
                140.0, repository.averageSessionDurationBetween(FROM, until)); // (120+240+60)/3
        assertEquals(3, repository.sessionSpans(FROM, until).size());
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
        assertEquals(1, repository.dailyWaitlist(FROM, until, BUCKET).size());
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
        assertEquals(1, repository.dailyRevenue(FROM, until, BUCKET).size());
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
                        GemPack.builder()
                                .productId("gems_" + UUID.randomUUID())
                                .gems(500)
                                .price(new java.math.BigDecimal("5000"))
                                .build());
        gemOrderRepository.save(
                GemOrder.builder()
                        .user(user)
                        .gemPack(pack)
                        .gems(500)
                        .amount(new java.math.BigDecimal("5000"))
                        .currency("ARS")
                        .status(GemOrderStatus.PAID)
                        .paidAt(Instant.now())
                        .build());
        organizationRepository.save(
                Organization.builder()
                        .name("Org " + UUID.randomUUID())
                        .status(OrganizationStatus.ACTIVE)
                        .build());

        assertEquals(1, repository.countGemPurchasesBetween(FROM, until));
        assertEquals(500, repository.sumGemsGrantedBetween(FROM, until));
        assertEquals(1, repository.dailyGemPurchases(FROM, until, BUCKET).size());
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

    @Test
    void aggregatesOrganizationsDeepDiveAndConversion() {
        CoursePurchase purchase = savePaidPurchase(new BigDecimal("12000.00"));
        Organization org = purchase.getOrganization();
        // A fixed future instant (no sub-microsecond nanos, so H2's timestamp round-trips exactly).
        Instant expiry = Instant.parse("2027-01-01T00:00:00Z");
        PurchaseSeat activeSeat = seat(purchase, SeatStatus.ACTIVE);
        activeSeat.setAccessExpiresAt(expiry);
        seatRepository.save(activeSeat);
        seatRepository.save(seat(purchase, SeatStatus.INVITED));
        User memberUser = saveUser();
        organizationMemberRepository.save(
                OrganizationMember.builder()
                        .organization(org)
                        .user(memberUser)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .joinedAt(Instant.now())
                        .build());
        activityRepository.save(
                UserActivity.builder().user(memberUser).occurredAt(Instant.now()).build());
        // A still-pending org for the "oldest pending" chip.
        organizationRepository.save(
                Organization.builder()
                        .name("Pending " + UUID.randomUUID())
                        .status(OrganizationStatus.PENDING)
                        .build());
        // A free-course enrollment feeds the conversion base.
        Course freeCourse =
                courseRepository.save(
                        Course.builder()
                                .code("free-" + UUID.randomUUID())
                                .name("Free")
                                .isFree(true)
                                .signLanguage(course.getSignLanguage())
                                .build());
        CourseVersion freeVersion =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1.0.0")
                                .status(VersionStatus.PUBLISHED)
                                .course(freeCourse)
                                .build());
        enrollmentRepository.save(
                UserCourseEnrollment.builder()
                        .user(saveUser())
                        .courseVersion(freeVersion)
                        .status(EnrollmentStatus.ENROLLED)
                        .startedAt(Instant.now())
                        .build());

        // Two orgs created in range: the ACTIVE buyer and the separate PENDING org.
        assertEquals(2, repository.countNewOrgsBetween(FROM, until));
        assertEquals(1, repository.membersByOrg(until).size());
        assertEquals(1L, ((Number) repository.membersByOrg(until).get(0)[2]).longValue());
        assertEquals(1, repository.revenueByOrg(until).size());
        var seats = repository.seatsByOrg(until).get(0);
        assertEquals(2L, ((Number) seats[2]).longValue()); // sold
        assertEquals(1L, ((Number) seats[3]).longValue()); // active
        assertEquals(1, repository.dailyPurchases(FROM, until, BUCKET).size());
        assertEquals(1, repository.countFreeCourseUsers(until));

        // New license-table columns + pending chip.
        var active7d = repository.activeMembersByOrg(until.minus(7, ChronoUnit.DAYS), until);
        assertEquals(1, active7d.size());
        assertEquals(org.getId(), active7d.get(0)[0]);
        assertEquals(1L, ((Number) active7d.get(0)[1]).longValue());
        var expiries = repository.nextSeatExpiryByOrg(until);
        assertEquals(1, expiries.size());
        assertEquals(expiry, expiries.get(0)[1]);
        // The buyer org is ACTIVE; a separate PENDING org makes the oldest-pending timestamp
        // present.
        assertEquals(true, repository.oldestPendingOrgCreatedAt(until) != null);
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

    private UserSession session(User sessionUser, int durationSeconds) {
        return UserSession.builder()
                .user(sessionUser)
                .startedAt(Instant.now())
                .durationSeconds(durationSeconds)
                .recordedAt(Instant.now())
                .build();
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
