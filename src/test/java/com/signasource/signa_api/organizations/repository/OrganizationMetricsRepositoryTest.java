package com.signasource.signa_api.organizations.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
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
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.SignLanguageRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
import com.signasource.signa_api.organizations.entity.MemberRole;
import com.signasource.signa_api.organizations.entity.MemberStatus;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.OrganizationMember;
import com.signasource.signa_api.organizations.repository.projection.AttemptTotalsView;
import com.signasource.signa_api.organizations.repository.projection.DailyAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.TopicStatusCountView;
import com.signasource.signa_api.organizations.repository.projection.UserAttemptStatsView;
import com.signasource.signa_api.organizations.repository.projection.UserCompletedLessonsView;
import com.signasource.signa_api.organizations.repository.projection.UserCountView;
import com.signasource.signa_api.organizations.repository.projection.VersionCountView;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.entity.UserDailyActivity;
import com.signasource.signa_api.users.repository.UserDailyActivityRepository;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** Runs the dashboard aggregates against H2 to check the JPQL, not just its syntax. */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class OrganizationMetricsRepositoryTest {

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private OrganizationMetricsRepository metricsRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private OrganizationMemberRepository memberRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private LessonBlockAttemptRepository attemptRepository;
    @Autowired private UserLessonProgressRepository lessonProgressRepository;
    @Autowired private UserTopicProgressRepository topicProgressRepository;
    @Autowired private UserDailyActivityRepository dailyActivityRepository;
    @Autowired private UserStatsRepository userStatsRepository;

    private Organization organization;
    private User ana;
    private User bruno;
    private CourseVersion version;
    private Topic topic;
    private Lesson lesson;
    private LessonBlock meaningBlock;
    private LessonBlock recognitionBlock;
    private LessonBlock infoBlock;

    @BeforeEach
    void setUp() {
        organization =
                organizationRepository.save(
                        Organization.builder().name("Metrics Org " + UUID.randomUUID()).build());
        ana = saveUser("ana");
        bruno = saveUser("bruno");

        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("M" + UUID.randomUUID().toString().substring(0, 8))
                                .name("Metrics language")
                                .countryCode("AR")
                                .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("metrics-" + UUID.randomUUID())
                                .name("Metrics course")
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
        meaningBlock = saveBlock(BlockType.SELECT_MEANING, 1);
        recognitionBlock = saveBlock(BlockType.VISUAL_RECOGNITION, 2);
        infoBlock = saveBlock(BlockType.INFO, 3);
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

    private LessonBlock saveBlock(BlockType type, int order) {
        return lessonBlockRepository.save(
                LessonBlock.builder().type(type).order(order).lesson(lesson).build());
    }

    private LessonBlockAttempt attempt(
            User user, LessonBlock block, Boolean correct, boolean tagged) {
        return attemptRepository.save(
                LessonBlockAttempt.builder()
                        .user(user)
                        .lessonBlock(block)
                        .isCorrect(correct)
                        .organization(tagged ? organization : null)
                        .build());
    }

    private void seedAttempts() {
        attempt(ana, meaningBlock, true, true);
        attempt(ana, meaningBlock, false, true);
        attempt(ana, recognitionBlock, true, true);
        attempt(ana, infoBlock, null, true);
        attempt(ana, meaningBlock, false, false);
        attempt(bruno, recognitionBlock, false, true);
    }

    private static <T> Map<UUID, T> byUser(List<T> values, Function<T, UUID> key) {
        return values.stream().collect(Collectors.toMap(key, Function.identity()));
    }

    @Test
    void shouldAggregateOrganizationAttemptsPerUserIgnoringUntaggedOnes() {
        seedAttempts();

        Map<UUID, UserAttemptStatsView> stats =
                byUser(
                        metricsRepository.findAttemptStatsByUser(
                                organization.getId(), List.of(ana.getId(), bruno.getId())),
                        UserAttemptStatsView::getUserId);

        assertEquals(3, stats.get(ana.getId()).getEvaluated());
        assertEquals(2, stats.get(ana.getId()).getCorrect());
        assertNotNull(stats.get(ana.getId()).getLastActivityAt());
        assertEquals(1, stats.get(bruno.getId()).getEvaluated());
        assertEquals(0, stats.get(bruno.getId()).getCorrect());
    }

    @Test
    void shouldAggregateEvaluatedAttemptsPerTopic() {
        seedAttempts();

        List<TopicAttemptStatsView> stats =
                metricsRepository.findAttemptStatsByTopic(
                        organization.getId(), List.of(ana.getId(), bruno.getId()));

        assertEquals(1, stats.size());
        assertEquals(topic.getId(), stats.get(0).getTopicId());
        assertEquals(4, stats.get(0).getEvaluated());
        assertEquals(2, stats.get(0).getCorrect());
    }

    @Test
    void shouldAggregateAttemptsByBlockType() {
        seedAttempts();

        AttemptTotalsView totals =
                metricsRepository.findAttemptTotalsByBlockTypes(
                        organization.getId(),
                        List.of(ana.getId(), bruno.getId()),
                        List.of(BlockType.VISUAL_RECOGNITION));
        AttemptTotalsView none =
                metricsRepository.findAttemptTotalsByBlockTypes(
                        organization.getId(),
                        List.of(ana.getId()),
                        List.of(BlockType.PERFORM_SIGN));

        assertEquals(2, totals.getEvaluated());
        assertEquals(1, totals.getCorrect());
        assertEquals(0, none.getEvaluated());
        assertEquals(0, none.getCorrect());
    }

    @Test
    void shouldGroupRecentAttemptsByDay() {
        seedAttempts();

        List<DailyAttemptStatsView> days =
                metricsRepository.findDailyAttemptStats(
                        organization.getId(),
                        List.of(ana.getId(), bruno.getId()),
                        Instant.now().minus(2, ChronoUnit.DAYS));

        assertEquals(1, days.size());
        assertEquals(4, days.get(0).getEvaluated());
        assertEquals(2, days.get(0).getCorrect());
        assertNotNull(days.get(0).getDay());
    }

    @Test
    void shouldCountCompletedLessonsAndModules() {
        lessonProgressRepository.save(
                UserLessonProgress.builder()
                        .user(ana)
                        .lesson(lesson)
                        .status(ProgressStatus.COMPLETED)
                        .build());
        topicProgressRepository.save(
                UserTopicProgress.builder()
                        .user(ana)
                        .topic(topic)
                        .status(ProgressStatus.COMPLETED)
                        .build());
        topicProgressRepository.save(
                UserTopicProgress.builder()
                        .user(bruno)
                        .topic(topic)
                        .status(ProgressStatus.IN_PROGRESS)
                        .startedAt(Instant.now())
                        .build());
        List<UUID> users = List.of(ana.getId(), bruno.getId());
        List<UUID> versions = List.of(version.getId());

        List<UserCompletedLessonsView> perUser =
                metricsRepository.findCompletedLessonCountsByUser(users, versions);
        List<VersionCountView> perVersion =
                metricsRepository.findCompletedLessonCountsByVersion(ana.getId(), versions);
        Map<ProgressStatus, Long> statuses =
                metricsRepository.findTopicStatusCounts(users, versions).stream()
                        .collect(
                                Collectors.toMap(
                                        TopicStatusCountView::getStatus,
                                        TopicStatusCountView::getUsers));
        List<UserTopicProgress> inProgress =
                metricsRepository.findInProgressTopics(users, versions);

        assertEquals(1, perUser.size());
        assertEquals(ana.getId(), perUser.get(0).getUserId());
        assertEquals(1, perUser.get(0).getCompletedLessons());
        assertEquals(version.getId(), perVersion.get(0).getVersionId());
        assertEquals(1, perVersion.get(0).getTotal());
        assertEquals(1, statuses.get(ProgressStatus.COMPLETED));
        assertEquals(1, statuses.get(ProgressStatus.IN_PROGRESS));
        assertEquals(1, inProgress.size());
        assertEquals(bruno.getId(), inProgress.get(0).getUser().getId());
        assertEquals("Greetings", inProgress.get(0).getTopic().getTitle());
    }

    @Test
    void shouldSumLearningMinutesActiveDaysAndReadStreaks() {
        dailyActivityRepository.save(
                UserDailyActivity.builder()
                        .user(ana)
                        .activityDate(LocalDate.now())
                        .minutes(5)
                        .build());
        dailyActivityRepository.save(
                UserDailyActivity.builder()
                        .user(ana)
                        .activityDate(LocalDate.now().minusDays(40))
                        .minutes(3)
                        .build());
        userStatsRepository.save(
                UserStats.builder().user(ana).currentStreak(4).updatedAt(Instant.now()).build());
        List<UUID> users = List.of(ana.getId(), bruno.getId());

        List<UserCountView> minutes = metricsRepository.findLearningMinutesByUser(users);
        List<UserCountView> activeDays =
                metricsRepository.findActiveDaysByUser(users, LocalDate.now().minusDays(30));
        List<Integer> streaks = metricsRepository.findCurrentStreaks(users);

        assertEquals(8, minutes.get(0).getTotal());
        assertEquals(1, activeDays.get(0).getTotal());
        assertEquals(List.of(4), streaks);
    }

    @Test
    void shouldSearchActiveMembersByNameOrEmail() {
        memberRepository.save(
                OrganizationMember.builder()
                        .organization(organization)
                        .user(ana)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.ACTIVE)
                        .build());
        memberRepository.save(
                OrganizationMember.builder()
                        .organization(organization)
                        .user(bruno)
                        .role(MemberRole.MEMBER)
                        .status(MemberStatus.REMOVED)
                        .build());

        var active =
                memberRepository.search(
                        organization.getId(),
                        MemberRole.MEMBER,
                        List.of(MemberStatus.ACTIVE),
                        "%",
                        org.springframework.data.domain.PageRequest.of(0, 10));
        var byName =
                memberRepository.search(
                        organization.getId(),
                        MemberRole.MEMBER,
                        List.of(MemberStatus.ACTIVE, MemberStatus.REMOVED),
                        "%brun%",
                        org.springframework.data.domain.PageRequest.of(0, 10));

        assertEquals(1, active.getTotalElements());
        assertEquals(ana.getId(), active.getContent().get(0).getUser().getId());
        assertEquals(1, byName.getTotalElements());
        assertTrue(byName.getContent().get(0).getUser().getName().equals("bruno"));
    }
}
