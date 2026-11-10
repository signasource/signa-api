package com.signasource.signa_api.metrics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.UserLessonProgress;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserActivity;
import com.signasource.signa_api.users.repository.UserActivityRepository;
import com.signasource.signa_api.users.repository.UserRepository;
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

/** Runs the global dashboard aggregates against H2 to check the JPQL, not just its syntax. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class GlobalMetricsRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private GlobalMetricsRepository repository;
    @Autowired private UserRepository userRepository;
    @Autowired private UserActivityRepository activityRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private LessonBlockAttemptRepository attemptRepository;
    @Autowired private UserCourseEnrollmentRepository enrollmentRepository;
    @Autowired private UserLessonProgressRepository lessonProgressRepository;
    @Autowired private UserTopicProgressRepository topicProgressRepository;

    private static final Instant FROM = Instant.parse("2000-01-01T00:00:00Z");
    private final Instant until = Instant.now().plus(1, ChronoUnit.DAYS);

    private User user;
    private CourseVersion version;
    private Topic topic;
    private Lesson lesson;
    private LessonBlock block;

    @BeforeEach
    void setUp() {
        user = saveUser();
        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("G" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Global language")
                                .countryCode("AR")
                                .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("global-" + UUID.randomUUID())
                                .name("Global course")
                                .signLanguage(language)
                                .build());
        version =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1.0.0")
                                .status(VersionStatus.PUBLISHED)
                                .course(course)
                                .build());
        topic =
                topicRepository.save(
                        Topic.builder()
                                .code("t1")
                                .title("Greetings")
                                .order(1)
                                .courseVersion(version)
                                .build());
        lesson =
                lessonRepository.save(
                        Lesson.builder().code("l1").name("Hello").order(1).topic(topic).build());
        block =
                lessonBlockRepository.save(
                        LessonBlock.builder()
                                .type(BlockType.SELECT_MEANING)
                                .order(1)
                                .lesson(lesson)
                                .build());
    }

    @Test
    void aggregatesCoursesEnrollmentsUsersAndAccuracy() {
        enrollmentRepository.save(
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .build());
        attemptRepository.save(attempt(true));
        attemptRepository.save(attempt(false));
        activityRepository.save(
                UserActivity.builder().user(user).occurredAt(java.time.Instant.now()).build());

        assertEquals(1, repository.countCourses());
        assertEquals(1, repository.countVersionsByStatus().size());
        assertEquals(VersionStatus.PUBLISHED, repository.countVersionsByStatus().get(0)[0]);
        assertEquals(1, repository.countEnrollmentsByStatus(until).size());
        assertTrue(repository.countEnabledUsers() >= 1);
        assertEquals(1, repository.countActiveUsers(FROM, until));
        assertEquals(2, repository.countEvaluatedAttemptsBetween(FROM, until));
        assertEquals(1, repository.countCorrectAttemptsBetween(FROM, until));
        assertEquals(1, repository.accuracyByBlockType(FROM, until).size());
        assertEquals(1, repository.dailyAttempts(FROM, until).size());
    }

    @Test
    void countsCompletedProgressWithinWindow() {
        lessonProgressRepository.save(
                UserLessonProgress.builder()
                        .user(user)
                        .lesson(lesson)
                        .status(ProgressStatus.COMPLETED)
                        .completedAt(Instant.now())
                        .build());
        topicProgressRepository.save(
                UserTopicProgress.builder()
                        .user(user)
                        .topic(topic)
                        .status(ProgressStatus.COMPLETED)
                        .completedAt(Instant.now())
                        .build());

        assertEquals(1, repository.countLessonsCompletedBetween(FROM, until));
        assertEquals(1, repository.countTopicsCompletedBetween(FROM, until));
        assertEquals(1, repository.dailyLessonsCompleted(FROM, until).size());
    }

    @Test
    void errorSurfaceQueriesRunAgainstEmptyTables() {
        assertNotNull(repository.countSignReportsByStatus());
        assertEquals(0, repository.countFailedImportsBetween(FROM, until));
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
                        .lastName("Global")
                        .enabled(true)
                        .verified(true)
                        .build());
    }
}
