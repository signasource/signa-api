package com.signasource.signa_api.organizations.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class PurchaseSeatRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private PurchaseSeatRepository seatRepository;
    @Autowired private CoursePurchaseRepository purchaseRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;

    private Organization organization;
    private Course course;
    private CourseVersion version;
    private Instant now;

    @BeforeEach
    void setUp() {
        now = Instant.now();
        organization =
                organizationRepository.save(
                        Organization.builder().name("Seat Org " + UUID.randomUUID()).build());
        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("S" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Seat language")
                                .countryCode("AR")
                                .build());
        course =
                courseRepository.save(
                        Course.builder()
                                .code("seats-" + UUID.randomUUID())
                                .name("Seats course")
                                .signLanguage(language)
                                .build());
        version =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1.0.0")
                                .status(VersionStatus.PUBLISHED)
                                .course(course)
                                .build());
    }

    private CoursePurchase purchase(Instant paidAt) {
        return purchaseRepository.save(
                CoursePurchase.builder()
                        .organization(organization)
                        .course(course)
                        .seats(1)
                        .durationDays(30)
                        .signCount(10)
                        .listPricePerSeat(BigDecimal.TEN)
                        .pricePerSeat(BigDecimal.TEN)
                        .totalAmount(BigDecimal.TEN)
                        .currency("ARS")
                        .status(
                                paidAt == null
                                        ? PurchaseStatus.PENDING_PAYMENT
                                        : PurchaseStatus.PAID)
                        .paidAt(paidAt)
                        .invitationDeadline(
                                paidAt == null ? null : paidAt.plus(10, ChronoUnit.DAYS))
                        .build());
    }

    private PurchaseSeat seat(CoursePurchase purchase, String email, SeatStatus status) {
        return seatRepository.save(
                PurchaseSeat.builder()
                        .purchase(purchase)
                        .email(email)
                        .status(status)
                        .invitationToken(UUID.randomUUID().toString())
                        .build());
    }

    @Test
    void shouldFindSeatsDueForTheAutomaticReminder() {
        CoursePurchase old = purchase(now.minus(6, ChronoUnit.DAYS));
        CoursePurchase recent = purchase(now.minus(2, ChronoUnit.DAYS));
        CoursePurchase closed = purchase(now.minus(11, ChronoUnit.DAYS));
        PurchaseSeat due = seat(old, "due@x.com", SeatStatus.INVITED);
        PurchaseSeat reminded = seat(old, "reminded@x.com", SeatStatus.INVITED);
        reminded.setAutoReminderSentAt(now.minus(1, ChronoUnit.DAYS));
        seatRepository.save(reminded);
        seat(old, "accepted@x.com", SeatStatus.ACTIVE);
        seat(recent, "recent@x.com", SeatStatus.INVITED);
        seat(closed, "closed@x.com", SeatStatus.INVITED);

        List<PurchaseSeat> result =
                seatRepository.findDueForAutoReminder(now.minus(5, ChronoUnit.DAYS), now);

        assertEquals(List.of(due.getId()), result.stream().map(PurchaseSeat::getId).toList());
    }

    @Test
    void shouldFindSeatsOfTheCourseByEmail() {
        CoursePurchase purchase = purchase(null);
        seat(purchase, "ana@x.com", SeatStatus.PENDING_PAYMENT);
        seat(purchase, "bruno@x.com", SeatStatus.CANCELLED);

        List<PurchaseSeat> result =
                seatRepository.findCourseSeatsByEmails(
                        organization.getId(),
                        course.getId(),
                        List.of("ana@x.com", "bruno@x.com"),
                        List.of(SeatStatus.PENDING_PAYMENT, SeatStatus.INVITED, SeatStatus.ACTIVE));

        assertEquals(List.of("ana@x.com"), result.stream().map(PurchaseSeat::getEmail).toList());
    }

    @Test
    void shouldListTheSignsIntroducedByAVersionInLearningOrder() {
        Topic second =
                topicRepository.save(
                        Topic.builder()
                                .code("t2")
                                .title("Emergencies")
                                .order(2)
                                .courseVersion(version)
                                .build());
        Topic first =
                topicRepository.save(
                        Topic.builder()
                                .code("t1")
                                .title("Greetings")
                                .order(1)
                                .courseVersion(version)
                                .build());
        Lesson pain =
                lessonRepository.save(
                        Lesson.builder().code("l2").name("Pain").order(1).topic(second).build());
        Lesson hello =
                lessonRepository.save(
                        Lesson.builder().code("l1").name("Hello").order(1).topic(first).build());
        LessonBlock dolor = introduce(pain, 1, "Dolor");
        LessonBlock gracias = introduce(hello, 2, "Gracias");
        LessonBlock hola = introduce(hello, 1, "Hola");
        lessonBlockRepository.save(
                LessonBlock.builder()
                        .type(BlockType.SELECT_MEANING)
                        .order(3)
                        .lesson(hello)
                        .build());

        List<LessonBlock> signs = lessonBlockRepository.findIntroducedSigns(version.getId());

        assertEquals(
                List.of(hola.getId(), gracias.getId(), dolor.getId()),
                signs.stream().map(LessonBlock::getId).toList());
        assertEquals("Greetings", signs.get(0).getLesson().getTopic().getTitle());
    }

    private LessonBlock introduce(Lesson lesson, int order, String word) {
        return lessonBlockRepository.save(
                LessonBlock.builder()
                        .type(BlockType.INTRODUCE_SIGN)
                        .order(order)
                        .lesson(lesson)
                        .config("{\"word\":\"" + word + "\"}")
                        .build());
    }
}
