package com.signasource.signa_api.metrics.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.SignLanguage;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserLessonProgress;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * The learning funnel counts the same universe as the growth "usuarios" metrics — every account but
 * SIGNA staff, verified or not — so "Usuarios registrados" matches the user total on the Resumen.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class LearningMetricsFunnelRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private LearningMetricsRepository repository;
    @Autowired private GrowthMetricsRepository growthRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private LessonBlockAttemptRepository attemptRepository;
    @Autowired private UserLessonProgressRepository progressRepository;

    private final Instant until = Instant.now().plus(1, ChronoUnit.DAYS);

    @Test
    void funnelExcludesStaffAndMatchesTheGrowthUserTotal() {
        long registered = repository.countUsersBefore(until);
        long started = repository.countUsersStartedLessonBefore(until);
        long attempted = repository.countUsersAttemptedBefore(until);
        long completed = repository.countUsersCompletedLessonBefore(until);
        int twoPlus = repository.usersWithTwoPlusCompletedLessons(until).size();

        List<Lesson> lessons = twoLessons();
        LessonBlock block =
                lessonBlockRepository.save(
                        LessonBlock.builder()
                                .type(BlockType.SELECT_MEANING)
                                .order(1)
                                .lesson(lessons.get(0))
                                .build());
        User learner = saveUser(Role.USER, true);
        saveUser(Role.USER, false); // signed up, not verified yet
        User staff = saveUser(Role.ADMIN, true);
        for (User u : List.of(learner, staff)) {
            attemptRepository.save(
                    LessonBlockAttempt.builder()
                            .user(u)
                            .lessonBlock(block)
                            .isCorrect(true)
                            .build());
            for (Lesson lesson : lessons) {
                progressRepository.save(completed(u, lesson));
            }
        }

        // The learner and the not-yet-verified account count; the admin never does.
        assertEquals(registered + 2, repository.countUsersBefore(until));
        assertEquals(started + 1, repository.countUsersStartedLessonBefore(until));
        assertEquals(attempted + 1, repository.countUsersAttemptedBefore(until));
        assertEquals(completed + 1, repository.countUsersCompletedLessonBefore(until));
        assertEquals(twoPlus + 1, repository.usersWithTwoPlusCompletedLessons(until).size());
        assertEquals(growthRepository.countUsersBefore(until), repository.countUsersBefore(until));
    }

    private List<Lesson> twoLessons() {
        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("F" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Funnel language")
                                .countryCode("AR")
                                .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("funnel-" + UUID.randomUUID())
                                .name("Funnel course")
                                .signLanguage(language)
                                .build());
        CourseVersion version =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1.0.0")
                                .status(VersionStatus.PUBLISHED)
                                .course(course)
                                .name("Funnel course")
                                .build());
        Topic topic =
                topicRepository.save(
                        Topic.builder()
                                .code("t1")
                                .title("Greetings")
                                .order(1)
                                .courseVersion(version)
                                .build());
        return List.of(
                lessonRepository.save(
                        Lesson.builder().code("l1").name("Hello").order(1).topic(topic).build()),
                lessonRepository.save(
                        Lesson.builder().code("l2").name("Bye").order(2).topic(topic).build()));
    }

    private static UserLessonProgress completed(User user, Lesson lesson) {
        return UserLessonProgress.builder()
                .user(user)
                .lesson(lesson)
                .status(ProgressStatus.COMPLETED)
                .startedAt(Instant.now().minus(1, ChronoUnit.HOURS))
                .completedAt(Instant.now())
                .xpEarned(10)
                .build();
    }

    private User saveUser(Role role, boolean enabled) {
        return userRepository.save(
                User.builder()
                        .email(UUID.randomUUID() + "@mail.com")
                        .username("u" + UUID.randomUUID())
                        .passwordHash("hash")
                        .name("Tester")
                        .lastName("Funnel")
                        .role(role)
                        .enabled(enabled)
                        .verified(enabled)
                        .build());
    }
}
