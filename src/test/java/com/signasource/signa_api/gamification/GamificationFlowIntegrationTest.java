package com.signasource.signa_api.gamification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.firebase.messaging.FirebaseMessaging;
import com.signasource.signa_api.common.ArgentinaTime;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.gamification.dto.AchievementResponse;
import com.signasource.signa_api.gamification.dto.ChallengeResponse;
import com.signasource.signa_api.gamification.dto.ChallengesResponse;
import com.signasource.signa_api.gamification.entity.Achievement;
import com.signasource.signa_api.gamification.entity.AchievementCriteriaType;
import com.signasource.signa_api.gamification.entity.Challenge;
import com.signasource.signa_api.gamification.entity.ChallengeCriteriaType;
import com.signasource.signa_api.gamification.entity.ChallengeRewardType;
import com.signasource.signa_api.gamification.entity.ChallengeType;
import com.signasource.signa_api.gamification.entity.UserChallenge;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.AchievementRepository;
import com.signasource.signa_api.gamification.repository.ChallengeRepository;
import com.signasource.signa_api.gamification.repository.UserChallengeRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.gamification.service.AchievementService;
import com.signasource.signa_api.gamification.service.ChallengeService;
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
import com.signasource.signa_api.learning.service.CourseTrackingService;
import com.signasource.signa_api.learning.service.PracticeService;
import com.signasource.signa_api.users.entity.Role;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import com.signasource.signa_api.users.service.FriendshipService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * End to end over the real services, listeners and transactions (H2): what the user does in the app
 * (answer blocks, practice, send/accept friend requests, come back another day) must move the first
 * steps, daily and weekly challenges, and grant achievements.
 */
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GamificationFlowIntegrationTest {

    /** XP per answered block; a lesson has two scored blocks → 200 XP. */
    private static final int BLOCK_XP = 100;

    @MockitoBean private FirebaseMessaging firebaseMessaging;

    @Autowired private CourseTrackingService courseTrackingService;
    @Autowired private PracticeService practiceService;
    @Autowired private FriendshipService friendshipService;
    @Autowired private ChallengeService challengeService;
    @Autowired private AchievementService achievementService;

    @Autowired private SignLanguageRepository signLanguageRepository;
    @Autowired private CourseRepository courseRepository;
    @Autowired private CourseVersionRepository courseVersionRepository;
    @Autowired private TopicRepository topicRepository;
    @Autowired private LessonRepository lessonRepository;
    @Autowired private LessonBlockRepository lessonBlockRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ChallengeRepository challengeRepository;
    @Autowired private AchievementRepository achievementRepository;
    @Autowired private UserChallengeRepository userChallengeRepository;
    @Autowired private UserStatsRepository userStatsRepository;

    private Topic topic;
    private CourseVersion version;
    private int lessonCount;

    @BeforeEach
    void setUp() {
        if (challengeRepository.count() == 0) {
            seedChallenges();
            seedAchievements();
        }
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        SignLanguage language =
                signLanguageRepository.save(
                        SignLanguage.builder()
                                .code("G" + suffix.substring(0, 6))
                                .name("Gamification")
                                .countryCode("TST")
                                .build());
        Course course =
                courseRepository.save(
                        Course.builder()
                                .code("course-" + suffix)
                                .name("Course")
                                .isFree(true)
                                .signLanguage(language)
                                .build());
        version =
                courseVersionRepository.save(
                        CourseVersion.builder()
                                .version("1")
                                .status(VersionStatus.PUBLISHED)
                                .course(course)
                                .build());
        topic =
                topicRepository.save(
                        Topic.builder()
                                .code("topic-" + suffix)
                                .title("Topic")
                                .order(0)
                                .courseVersion(version)
                                .build());
        // Extra lesson so finishing the test lessons never completes the topic / course.
        lesson();
    }

    // ── First steps ──────────────────────────────────────────────────────

    @Test
    void firstSteps_ProgressFromRealActivity_ClaimUnlocksDailyAndWeekly() {
        User user = newUser();
        User friend = newUser();

        ChallengesResponse start = challengeService.getChallenges(user);
        assertThat(start.firstSteps()).hasSize(4).noneMatch(ChallengeResponse::completed);
        assertThat(start.firstStepsDone()).isFalse();
        assertThat(start.daily()).isEmpty();
        assertThat(start.weekly()).isEmpty();

        // Can't claim before completing.
        UUID lessonStepId = byCode(start.firstSteps(), "first_steps_lesson").id();
        assertThatThrownBy(() -> challengeService.claim(lessonStepId, user))
                .isInstanceOf(InvalidInputException.class);

        // Day 1: a lesson with a correct camera answer, and a friend request.
        playLesson(user, lesson(), true);
        friendshipService.sendFriendRequest(user, friend.getId());

        ChallengesResponse day1 = challengeService.getChallenges(user);
        assertThat(byCode(day1.firstSteps(), "first_steps_lesson").completed()).isTrue();
        assertThat(byCode(day1.firstSteps(), "first_steps_camera").completed()).isTrue();
        assertThat(byCode(day1.firstSteps(), "first_steps_friend").completed()).isTrue();
        assertThat(byCode(day1.firstSteps(), "first_steps_streak").progress()).isEqualTo(1);
        assertThat(byCode(day1.firstSteps(), "first_steps_streak").completed()).isFalse();

        // Day 2: coming back extends the streak to 2.
        comeBackTomorrow(user);
        playLesson(user, lesson(), true);
        ChallengesResponse day2 = challengeService.getChallenges(user);
        assertThat(day2.firstSteps()).allMatch(ChallengeResponse::completed);
        assertThat(day2.firstStepsDone()).as("not claimed yet").isFalse();
        assertThat(day2.daily()).isEmpty();

        int gemsBefore = stats(user).getGems();
        int lastBalance = 0;
        for (ChallengeResponse step : day2.firstSteps()) {
            lastBalance = challengeService.claim(step.id(), user).gems();
        }
        assertThat(lastBalance).isEqualTo(gemsBefore + 100);
        assertThat(stats(user).getGems()).isEqualTo(gemsBefore + 100);

        // Double claim is rejected and pays nothing.
        assertThatThrownBy(() -> challengeService.claim(lessonStepId, user))
                .isInstanceOf(InvalidInputException.class);
        assertThat(stats(user).getGems()).isEqualTo(gemsBefore + 100);

        ChallengesResponse unlocked = challengeService.getChallenges(user);
        assertThat(unlocked.firstStepsDone()).isTrue();
        assertThat(unlocked.daily())
                .extracting(ChallengeResponse::code)
                .containsExactly("daily_lessons", "daily_camera", "daily_perfect", "daily_xp");
        assertThat(unlocked.weekly())
                .extracting(ChallengeResponse::code)
                .containsExactly("weekly_lessons", "weekly_camera", "weekly_perfect", "weekly_xp");
    }

    // ── Daily & weekly ───────────────────────────────────────────────────

    @Test
    void dailyAndWeekly_ProgressCompleteAndPayOut() {
        User user = userWithFirstStepsDone();

        // 3 perfect lessons (each: 1 camera answer, 200 XP) + 2 camera answers in free practice.
        List<UUID> cameraBlocks = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            List<UUID> blocks = lesson();
            playLesson(user, blocks, true);
            cameraBlocks.add(blocks.get(2));
        }
        practiceService.recordAttempt(user, cameraBlocks.get(0), true);
        practiceService.recordAttempt(user, cameraBlocks.get(1), true);
        practiceService.recordAttempt(user, cameraBlocks.get(2), false); // wrong: doesn't count

        ChallengesResponse after = challengeService.getChallenges(user);
        assertThat(byCode(after.daily(), "daily_lessons").completed()).isTrue();
        assertThat(byCode(after.daily(), "daily_camera").progress()).isEqualTo(5);
        assertThat(byCode(after.daily(), "daily_camera").completed()).isTrue();
        assertThat(byCode(after.daily(), "daily_perfect").completed()).isTrue();
        assertThat(byCode(after.daily(), "daily_xp").progress()).isEqualTo(500); // capped
        assertThat(byCode(after.daily(), "daily_xp").completed()).isTrue();

        assertThat(byCode(after.weekly(), "weekly_lessons").progress()).isEqualTo(3);
        assertThat(byCode(after.weekly(), "weekly_camera").progress()).isEqualTo(5);
        assertThat(byCode(after.weekly(), "weekly_perfect").progress()).isEqualTo(3);
        assertThat(byCode(after.weekly(), "weekly_xp").progress()).isEqualTo(600);
        assertThat(after.weekly()).noneMatch(ChallengeResponse::completed);

        int gems = stats(user).getGems();
        challengeService.claim(byCode(after.daily(), "daily_lessons").id(), user);
        challengeService.claim(byCode(after.daily(), "daily_camera").id(), user);
        challengeService.claim(byCode(after.daily(), "daily_xp").id(), user);
        assertThat(stats(user).getGems()).isEqualTo(gems + 50 + 40 + 60);

        challengeService.claim(byCode(after.daily(), "daily_perfect").id(), user);
        assertThat(stats(user).getEffectiveXpMultiplier()).isEqualTo(3.0);

        // Weekly not completed yet → can't be claimed.
        UUID weeklyLessons = byCode(after.weekly(), "weekly_lessons").id();
        assertThatThrownBy(() -> challengeService.claim(weeklyLessons, user))
                .isInstanceOf(InvalidInputException.class);
    }

    @Test
    void lessonWithAMistake_IsNotPerfect() {
        User user = userWithFirstStepsDone();

        playLesson(user, lesson(), false);

        ChallengesResponse after = challengeService.getChallenges(user);
        assertThat(byCode(after.daily(), "daily_lessons").progress()).isEqualTo(1);
        assertThat(byCode(after.daily(), "daily_perfect").progress()).isZero();
        assertThat(byCode(after.weekly(), "weekly_perfect").progress()).isZero();
    }

    @Test
    void weekly_CompletesAndPaysAStreakShield() {
        User user = userWithFirstStepsDone();
        for (int i = 0; i < 7; i++) {
            playLesson(user, lesson(), true);
        }

        ChallengesResponse after = challengeService.getChallenges(user);
        ChallengeResponse perfect = byCode(after.weekly(), "weekly_perfect");
        assertThat(perfect.completed()).isTrue();

        int shields = stats(user).getStreakShields();
        challengeService.claim(perfect.id(), user);
        assertThat(stats(user).getStreakShields()).isEqualTo(shields + 1);
    }

    @Test
    void dailyRollover_ResetsDailyButKeepsWeekly() {
        User user = userWithFirstStepsDone();
        playLesson(user, lesson(), true);

        endTheDay(user);

        ChallengesResponse tomorrow = challengeService.getChallenges(user);
        assertThat(tomorrow.daily()).allMatch(c -> c.progress() == 0 && !c.completed());
        assertThat(byCode(tomorrow.weekly(), "weekly_lessons").progress()).isEqualTo(1);
    }

    @Test
    void dailyRollover_YesterdaysUnclaimedRewardIsGone() {
        User user = userWithFirstStepsDone();
        for (int i = 0; i < 3; i++) {
            playLesson(user, lesson(), true);
        }
        UUID yesterdays =
                byCode(challengeService.getChallenges(user).daily(), "daily_lessons").id();

        endTheDay(user);

        int gems = stats(user).getGems();
        assertThatThrownBy(() -> challengeService.claim(yesterdays, user))
                .as("docs: an unclaimed daily reward is gone after the rollover")
                .isInstanceOf(InvalidInputException.class);
        assertThat(stats(user).getGems()).isEqualTo(gems);
    }

    @Test
    void weeklyRollover_ResetsWeekly() {
        User user = userWithFirstStepsDone();
        playLesson(user, lesson(), true);

        LocalDate weekStart = ArgentinaTime.startOfCurrentWeekDate();
        shiftRows(user, ChallengeType.WEEKLY, weekStart, weekStart.minusWeeks(1));

        assertThat(challengeService.getChallenges(user).weekly())
                .allMatch(c -> c.progress() == 0 && !c.completed());
    }

    @Test
    void xpMultiplierReward_MultipliesEarnedXp() {
        User user = userWithFirstStepsDone();
        for (int i = 0; i < 3; i++) {
            playLesson(user, lesson(), true);
        }
        challengeService.claim(
                byCode(challengeService.getChallenges(user).daily(), "daily_perfect").id(), user);
        assertThat(stats(user).getEffectiveXpMultiplier()).isEqualTo(3.0);

        long before = stats(user).getTotalXp();
        playLesson(user, lesson(), true);

        assertThat(stats(user).getTotalXp() - before)
                .as("'Sesión perfecta' pays XP x3: a 200 XP lesson should give 600")
                .isEqualTo(3L * 2 * BLOCK_XP);
    }

    // ── Achievements ─────────────────────────────────────────────────────

    @Test
    void achievements_GrantedOnce_CreditRewards_AndCelebrateUntilSeen() {
        User user = newUser();

        playLesson(user, lesson(), true);

        List<AchievementResponse> unseen = achievementService.getUnseen(user);
        assertThat(unseen).extracting(AchievementResponse::code).containsExactly("FIRST_LESSON");
        assertThat(stats(user).getGems()).isEqualTo(10);

        achievementService.markSeen(unseen.get(0).id(), user);
        assertThat(achievementService.getUnseen(user)).isEmpty();

        // A second lesson doesn't grant it again.
        playLesson(user, lesson(), true);
        assertThat(achievementService.getUnseen(user)).isEmpty();
        assertThat(stats(user).getGems()).isEqualTo(10);
        assertThat(achievementService.getAchievements(user, true, true))
                .extracting(AchievementResponse::code)
                .containsExactly("FIRST_LESSON");

        // 3rd lesson: 600 XP total → TOTAL_XP 500.
        playLesson(user, lesson(), true);
        assertThat(achievementService.getUnseen(user))
                .extracting(AchievementResponse::code)
                .containsExactly("XP_500");
    }

    @Test
    void streakAchievement_OnThirdDay_GrantsAShield() {
        User user = newUser();
        playLesson(user, lesson(), true);
        comeBackTomorrow(user);
        playLesson(user, lesson(), true);
        comeBackTomorrow(user);
        int shields = stats(user).getStreakShields();
        playLesson(user, lesson(), true);

        assertThat(stats(user).getCurrentStreak()).isEqualTo(3);
        assertThat(achievementService.getUnseen(user))
                .extracting(AchievementResponse::code)
                .contains("STREAK_3");
        assertThat(stats(user).getStreakShields()).isEqualTo(shields + 1);
    }

    @Test
    void friendAchievement_GrantedToBothOnAccept() {
        User user = newUser();
        User friend = newUser();

        friendshipService.sendFriendRequest(user, friend.getId());
        assertThat(achievementService.getUnseen(user)).isEmpty();

        friendshipService.acceptFriendRequest(user.getId(), friend);

        assertThat(achievementService.getUnseen(user))
                .extracting(AchievementResponse::code)
                .containsExactly("FIRST_FRIEND");
        assertThat(achievementService.getUnseen(friend))
                .extracting(AchievementResponse::code)
                .containsExactly("FIRST_FRIEND");
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    /** Creates a lesson [INFO, SELECT_MEANING, PERFORM_SIGN] and returns its block ids. */
    private List<UUID> lesson() {
        Lesson lesson =
                lessonRepository.save(
                        Lesson.builder()
                                .code("lesson-" + lessonCount)
                                .name("Lesson")
                                .order(lessonCount++)
                                .topic(topic)
                                .build());
        return List.of(
                block(lesson, BlockType.INFO, 0, "{\"text\":\"Hola\"}", null),
                block(
                        lesson,
                        BlockType.SELECT_MEANING,
                        1,
                        "{\"sign\":\"hola\",\"options\":[\"hola\",\"chau\"]}",
                        BLOCK_XP),
                block(lesson, BlockType.PERFORM_SIGN, 2, "{\"sign\":\"hola\"}", BLOCK_XP));
    }

    private UUID block(Lesson lesson, BlockType type, int order, String config, Integer xp) {
        return lessonBlockRepository
                .save(
                        LessonBlock.builder()
                                .lesson(lesson)
                                .type(type)
                                .order(order)
                                .config(config)
                                .xpReward(xp)
                                .build())
                .getId();
    }

    /** Answers every block; when not {@code perfect}, the first scored block is missed once. */
    private void playLesson(User user, List<UUID> blocks, boolean perfect) {
        courseTrackingService.recordBlockInteraction(user, blocks.get(0), null);
        if (!perfect) {
            courseTrackingService.recordBlockInteraction(user, blocks.get(1), false);
        }
        courseTrackingService.recordBlockInteraction(user, blocks.get(1), true);
        courseTrackingService.recordBlockInteraction(user, blocks.get(2), true);
    }

    private User newUser() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User user =
                userRepository.save(
                        User.builder()
                                .email(suffix + "@example.com")
                                .username("u" + suffix)
                                .passwordHash("hashed")
                                .name("Test")
                                .lastName("User")
                                .role(Role.USER)
                                .enabled(true)
                                .verified(true)
                                .build());
        courseTrackingService.enrollUserInCourse(user, version.getId());
        return userRepository.findById(user.getId()).orElseThrow();
    }

    /** A user whose first steps are claimed, so daily/weekly are visible; stats start clean. */
    private User userWithFirstStepsDone() {
        User user = newUser();
        challengeService.getChallenges(user);
        userChallengeRepository
                .findByUserAndPeriodStartIn(user, List.of(LocalDate.EPOCH))
                .forEach(
                        row -> {
                            row.setCurrentProgress(row.getChallenge().getCriteriaValue());
                            row.setCompleted(true);
                            row.setRewardClaimedAt(java.time.Instant.now());
                            userChallengeRepository.save(row);
                        });
        assertThat(challengeService.getChallenges(user).firstStepsDone()).isTrue();
        return user;
    }

    /** Pretends the last activity was yesterday, so the next XP is a new streak day. */
    private void comeBackTomorrow(User user) {
        UserStats stats = stats(user);
        stats.setLastStreakDate(ArgentinaTime.today().minusDays(1));
        userStatsRepository.save(stats);
    }

    /** Moves today's daily rows to yesterday, as if midnight had passed. */
    private void endTheDay(User user) {
        LocalDate today = ArgentinaTime.today();
        shiftRows(user, ChallengeType.DAILY, today, today.minusDays(1));
    }

    private void shiftRows(User user, ChallengeType type, LocalDate from, LocalDate to) {
        for (UserChallenge row :
                userChallengeRepository.findByUserAndPeriodStartIn(user, List.of(from))) {
            if (row.getChallenge().getChallengeType() == type) {
                long days = java.time.temporal.ChronoUnit.DAYS.between(from, to);
                row.setPeriodStart(to);
                row.setPeriodEnd(row.getPeriodEnd().plusDays(days));
                userChallengeRepository.save(row);
            }
        }
    }

    private UserStats stats(User user) {
        return userStatsRepository.findByUserId(user.getId()).orElseThrow();
    }

    private static ChallengeResponse byCode(List<ChallengeResponse> list, String code) {
        return list.stream().filter(c -> c.code().equals(code)).findFirst().orElseThrow();
    }

    /** Mirrors db/seed/challenges.sql (the seed is Postgres-only). */
    private void seedChallenges() {
        challengeRepository.saveAll(
                List.of(
                        challenge(
                                "first_steps_lesson",
                                ChallengeType.FIRST_STEPS,
                                ChallengeCriteriaType.COMPLETE_LESSONS,
                                1,
                                ChallengeRewardType.GEMS,
                                20,
                                1),
                        challenge(
                                "first_steps_camera",
                                ChallengeType.FIRST_STEPS,
                                ChallengeCriteriaType.CAMERA_PRACTICES,
                                1,
                                ChallengeRewardType.GEMS,
                                20,
                                2),
                        challenge(
                                "first_steps_friend",
                                ChallengeType.FIRST_STEPS,
                                ChallengeCriteriaType.FRIEND_REQUESTS_SENT,
                                1,
                                ChallengeRewardType.GEMS,
                                30,
                                3),
                        challenge(
                                "first_steps_streak",
                                ChallengeType.FIRST_STEPS,
                                ChallengeCriteriaType.STREAK_DAYS,
                                2,
                                ChallengeRewardType.GEMS,
                                30,
                                4),
                        challenge(
                                "daily_lessons",
                                ChallengeType.DAILY,
                                ChallengeCriteriaType.COMPLETE_LESSONS,
                                3,
                                ChallengeRewardType.GEMS,
                                50,
                                1),
                        challenge(
                                "daily_camera",
                                ChallengeType.DAILY,
                                ChallengeCriteriaType.CAMERA_PRACTICES,
                                5,
                                ChallengeRewardType.GEMS,
                                40,
                                2),
                        multiplier(
                                challenge(
                                        "daily_perfect",
                                        ChallengeType.DAILY,
                                        ChallengeCriteriaType.PERFECT_LESSONS,
                                        3,
                                        ChallengeRewardType.XP_MULTIPLIER,
                                        1,
                                        3)),
                        challenge(
                                "daily_xp",
                                ChallengeType.DAILY,
                                ChallengeCriteriaType.EARN_XP,
                                500,
                                ChallengeRewardType.GEMS,
                                60,
                                4),
                        challenge(
                                "weekly_lessons",
                                ChallengeType.WEEKLY,
                                ChallengeCriteriaType.COMPLETE_LESSONS,
                                15,
                                ChallengeRewardType.GEMS,
                                150,
                                1),
                        challenge(
                                "weekly_camera",
                                ChallengeType.WEEKLY,
                                ChallengeCriteriaType.CAMERA_PRACTICES,
                                30,
                                ChallengeRewardType.GEMS,
                                120,
                                2),
                        challenge(
                                "weekly_perfect",
                                ChallengeType.WEEKLY,
                                ChallengeCriteriaType.PERFECT_LESSONS,
                                7,
                                ChallengeRewardType.STREAK_SHIELD,
                                1,
                                3),
                        challenge(
                                "weekly_xp",
                                ChallengeType.WEEKLY,
                                ChallengeCriteriaType.EARN_XP,
                                2500,
                                ChallengeRewardType.GEMS,
                                200,
                                4)));
    }

    private static Challenge challenge(
            String code,
            ChallengeType type,
            ChallengeCriteriaType criteria,
            int target,
            ChallengeRewardType reward,
            int quantity,
            int order) {
        return Challenge.builder()
                .code(code)
                .title(code)
                .challengeType(type)
                .criteriaType(criteria)
                .criteriaValue(target)
                .rewardType(reward)
                .rewardQuantity(quantity)
                .displayOrder(order)
                .build();
    }

    private static Challenge multiplier(Challenge challenge) {
        challenge.setRewardDurationMinutes(30);
        challenge.setRewardMultiplierValue(3.0);
        return challenge;
    }

    /** Subset of db/seed/achievements.sql. */
    private void seedAchievements() {
        achievementRepository.saveAll(
                List.of(
                        achievement(
                                "FIRST_LESSON",
                                AchievementCriteriaType.LESSONS_COMPLETED,
                                1,
                                0,
                                10),
                        achievement(
                                "LESSONS_25", AchievementCriteriaType.LESSONS_COMPLETED, 25, 0, 30),
                        achievement("STREAK_3", AchievementCriteriaType.STREAK_DAYS, 3, 1, 0),
                        achievement("XP_500", AchievementCriteriaType.TOTAL_XP, 500, 0, 0),
                        achievement(
                                "FIRST_FRIEND", AchievementCriteriaType.FRIENDS_COUNT, 1, 0, 0)));
    }

    private static Achievement achievement(
            String code, AchievementCriteriaType type, int value, int shields, int gems) {
        return Achievement.builder()
                .code(code)
                .title(code)
                .criteriaType(type)
                .criteriaValue(value)
                .rewardStreakShields(shields)
                .rewardGems(gems)
                .build();
    }
}
