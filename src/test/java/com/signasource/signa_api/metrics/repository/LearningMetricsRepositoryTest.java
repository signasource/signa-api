package com.signasource.signa_api.metrics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportStatus;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import com.signasource.signa_api.content.repository.ContentImportRunRepository;
import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Handedness;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.Sign;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.SignReport;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.SignReportRepository;
import com.signasource.signa_api.learning.repository.SignRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Runs the learning dashboard aggregates against H2 to check the JPQL, not just its syntax. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningMetricsRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private LearningMetricsRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private LessonBlockAttemptRepository attemptRepository;
    @Autowired private UserCourseEnrollmentRepository enrollmentRepository;
    @Autowired private UserLearnedSignRepository learnedSignRepository;
    @Autowired private SignRepository signRepository;
    @Autowired private SignReportRepository signReportRepository;
    @Autowired private ContentImportRunRepository importRunRepository;

    private static final Instant FROM = Instant.parse("2000-01-01T00:00:00Z");
    private final Instant until = Instant.now().plus(1, ChronoUnit.DAYS);

    private SignLanguage language;
    private CourseVersion version;
    private Lesson lesson;
    private LessonBlock block;
    private User user;

    @BeforeEach
    void setUp() {
        user = saveUser();
        language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("L" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Lengua")
                                .countryCode("AR")
                                .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("learn-" + UUID.randomUUID())
                                .name("Curso aprendizaje")
                                .signLanguage(language)
                                .build());
        version =
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
                                .title("Saludos")
                                .order(1)
                                .courseVersion(version)
                                .build());
        lesson =
                lessonRepository.save(
                        Lesson.builder().code("l1").name("Hola").order(1).topic(topic).build());
        block =
                lessonBlockRepository.save(
                        LessonBlock.builder()
                                .type(BlockType.SELECT_MEANING)
                                .order(1)
                                .lesson(lesson)
                                .build());
    }

    @Test
    void groupsEnrollmentsByCourseAndStatus() {
        enrollmentRepository.save(
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .startedAt(Instant.now())
                        .build());

        assertEquals(1, repository.enrollmentsByCourse(until).size());
        assertEquals(1, repository.enrollmentsByStatus(until).size());
        assertEquals(EnrollmentStatus.ENROLLED, repository.enrollmentsByStatus(until).get(0)[0]);
    }

    @Test
    void computesLessonAccuracy() {
        attemptRepository.save(attempt(true));
        attemptRepository.save(attempt(false));

        var rows = repository.lessonAccuracy(FROM, until, 1);
        assertEquals(1, rows.size());
        assertEquals(2L, ((Number) rows.get(0)[2]).longValue());
        assertEquals(1L, ((Number) rows.get(0)[3]).longValue());
    }

    @Test
    void countsLearnedSigns() {
        learnedSignRepository.save(
                UserLearnedSign.builder()
                        .user(user)
                        .sign("hola")
                        .courseVersion(version)
                        .learnedAt(Instant.now())
                        .build());

        assertEquals(1, repository.countLearnedSignsBetween(FROM, until));
        assertEquals(1, repository.dailyLearnedSigns(FROM, until).size());
    }

    @Test
    void readsResolvedReportTimesAndMostReported() {
        Sign sign =
                signRepository.save(
                        Sign.builder()
                                .meaning("hola-" + UUID.randomUUID())
                                .handedness(Handedness.ONE_HANDED)
                                .signLanguage(language)
                                .build());
        SignReport report = new SignReport();
        report.setSign(sign);
        report.setUser(user);
        report.setReason(ReportReason.WRONG_MEANING);
        report.setStatus(ReportStatus.RESOLVED);
        report.setCreatedAt(LocalDateTime.of(2026, 11, 1, 0, 0));
        report.setResolvedAt(LocalDateTime.of(2026, 11, 1, 3, 0));
        signReportRepository.save(report);

        assertEquals(1, repository.resolvedReportTimes().size());
        assertEquals(1, repository.mostReportedSigns().size());
        assertEquals(sign.getMeaning(), repository.mostReportedSigns().get(0)[0]);
    }

    @Test
    void groupsImportsByStatus() {
        importRunRepository.save(
                ContentImportRun.builder()
                        .startedAt(Instant.now())
                        .finishedAt(Instant.now())
                        .trigger(ContentImportTrigger.STARTUP)
                        .status(ContentImportStatus.SUCCESS)
                        .build());

        assertEquals(1, repository.importsByStatus(FROM, until).size());
        assertEquals(
                ContentImportStatus.SUCCESS, repository.importsByStatus(FROM, until).get(0)[0]);
    }

    private LessonBlockAttempt attempt(boolean correct) {
        return LessonBlockAttempt.builder()
                .user(user)
                .lessonBlock(block)
                .isCorrect(correct)
                .build();
    }

    private User saveUser() {
        return userRepository.save(
                User.builder()
                        .email(UUID.randomUUID() + "@mail.com")
                        .username("u" + UUID.randomUUID())
                        .passwordHash("hash")
                        .name("Tester")
                        .lastName("Learning")
                        .enabled(true)
                        .verified(true)
                        .build());
    }
}
