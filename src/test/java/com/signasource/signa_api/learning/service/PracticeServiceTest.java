package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.gamification.entity.UserLearnedSign;
import com.signasource.signa_api.gamification.entity.UserStats;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.gamification.repository.UserStatsRepository;
import com.signasource.signa_api.learning.dto.LearnedSignResponse;
import com.signasource.signa_api.learning.dto.LessonBlockResponse;
import com.signasource.signa_api.learning.dto.PracticeMistakeResponse;
import com.signasource.signa_api.learning.dto.PracticeSummaryResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.PracticeAttempt;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.PracticeAttemptRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.util.BlockSignExtractor;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class PracticeServiceTest {

    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private LessonBlockAttemptRepository lessonBlockAttemptRepository;
    @Mock private PracticeAttemptRepository practiceAttemptRepository;
    @Mock private UserCourseEnrollmentRepository userCourseEnrollmentRepository;
    @Mock private UserLearnedSignRepository userLearnedSignRepository;
    @Mock private UserStatsRepository userStatsRepository;
    @Mock private BlockSignExtractor blockSignExtractor;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private PracticeService practiceService;

    private User mockUser;
    private UUID userId;
    private UUID courseVersionId;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseVersionId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
    }

    private LessonBlock block(BlockType type) {
        Topic topic =
                Topic.builder()
                        .courseVersion(CourseVersion.builder().id(courseVersionId).build())
                        .build();
        Lesson lesson = Lesson.builder().topic(topic).build();
        return LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(type)
                .order(0)
                .config("{}")
                .xpReward(10)
                .lesson(lesson)
                .build();
    }

    private void givenEnrolled(List<LessonBlock> blocks) {
        UserCourseEnrollment enrollment =
                UserCourseEnrollment.builder()
                        .courseVersion(CourseVersion.builder().id(courseVersionId).build())
                        .build();
        when(userCourseEnrollmentRepository.findByUserId(userId)).thenReturn(List.of(enrollment));
        when(lessonBlockRepository.findByLessonTopicCourseVersionIdIn(List.of(courseVersionId)))
                .thenReturn(blocks);
    }

    @Test
    void getExercisesByType_ShouldRejectNonPracticableTypes() {
        assertThrows(
                InvalidInputException.class,
                () -> practiceService.getExercisesByType(mockUser, BlockType.INFO, 10));
        assertThrows(
                InvalidInputException.class,
                () -> practiceService.getExercisesByType(mockUser, BlockType.INTRODUCE_SIGN, 10));
        assertThrows(
                InvalidInputException.class,
                () -> practiceService.getExercisesByType(mockUser, BlockType.INVISIBLE_SIGNS, 10));
    }

    @Test
    void getExercisesByType_ShouldReturnEmpty_WhenUserHasNoEnrollments() {
        when(userCourseEnrollmentRepository.findByUserId(userId)).thenReturn(List.of());

        List<LessonBlockResponse> result =
                practiceService.getExercisesByType(mockUser, BlockType.MATCH, 10);

        assertTrue(result.isEmpty());
    }

    @Test
    void getExercisesByType_ShouldFilterByTypeAndClampLimit() {
        LessonBlock matchBlock = block(BlockType.MATCH);
        LessonBlock selectBlock = block(BlockType.SELECT_MEANING);
        givenEnrolled(List.of(matchBlock, selectBlock));

        List<LessonBlockResponse> result =
                practiceService.getExercisesByType(mockUser, BlockType.MATCH, 100);

        assertEquals(1, result.size());
        assertEquals(matchBlock.getId(), result.get(0).id());
    }

    @Test
    void getLearnedSigns_ShouldDeduplicatePreservingOrder() {
        UserLearnedSign hola =
                UserLearnedSign.builder()
                        .user(mockUser)
                        .sign("Hola")
                        .learnedAt(Instant.now())
                        .build();
        UserLearnedSign holaAgain =
                UserLearnedSign.builder()
                        .user(mockUser)
                        .sign("Hola")
                        .learnedAt(Instant.now())
                        .build();
        UserLearnedSign chau =
                UserLearnedSign.builder()
                        .user(mockUser)
                        .sign("Chau")
                        .learnedAt(Instant.now())
                        .build();
        when(userLearnedSignRepository.findByUserOrderByLearnedAtDesc(any(), any()))
                .thenReturn(List.of(hola, holaAgain, chau));

        List<LearnedSignResponse> result = practiceService.getLearnedSigns(mockUser, 50);

        assertEquals(
                List.of(new LearnedSignResponse("Hola"), new LearnedSignResponse("Chau")), result);
    }

    @Test
    void getExercisesForSign_ShouldKeepOnlyBlocksTeachingThatSign() {
        LessonBlock holaBlock = block(BlockType.SELECT_MEANING);
        LessonBlock chauBlock = block(BlockType.SELECT_MEANING);
        givenEnrolled(List.of(holaBlock, chauBlock));
        when(blockSignExtractor.extract(holaBlock)).thenReturn(List.of("Hola"));
        when(blockSignExtractor.extract(chauBlock)).thenReturn(List.of("Chau"));

        List<LessonBlockResponse> result =
                practiceService.getExercisesForSign(mockUser, "hola", 10);

        assertEquals(1, result.size());
        assertEquals(holaBlock.getId(), result.get(0).id());
    }

    @Test
    void getMistakes_ShouldExcludeBlocksWhoseLatestAttemptWasCorrect() {
        LessonBlock resolvedBlock = block(BlockType.MATCH);
        LessonBlock pendingBlock = block(BlockType.SELECT_SIGN);
        Instant now = Instant.now();

        LessonBlockAttempt resolvedWrong =
                LessonBlockAttempt.builder()
                        .lessonBlock(resolvedBlock)
                        .isCorrect(false)
                        .attemptedAt(now.minus(2, ChronoUnit.HOURS))
                        .build();
        when(lessonBlockAttemptRepository.findByUserIdOrderByAttemptedAtDesc(userId))
                .thenReturn(List.of(resolvedWrong));

        PracticeAttempt resolvedFixed =
                PracticeAttempt.builder()
                        .lessonBlock(resolvedBlock)
                        .isCorrect(true)
                        .attemptedAt(now.minus(1, ChronoUnit.HOURS))
                        .build();
        PracticeAttempt pendingWrong =
                PracticeAttempt.builder()
                        .lessonBlock(pendingBlock)
                        .isCorrect(false)
                        .attemptedAt(now)
                        .build();
        when(practiceAttemptRepository.findByUserIdOrderByAttemptedAtDesc(userId))
                .thenReturn(List.of(pendingWrong, resolvedFixed));

        List<PracticeMistakeResponse> result = practiceService.getMistakes(mockUser, 20);

        assertEquals(1, result.size());
        assertEquals(pendingBlock.getId(), result.get(0).lessonBlockId());
        assertEquals(1, result.get(0).misses());
    }

    @Test
    void getMistakes_ShouldIgnoreInfoBlockViews() {
        LessonBlock infoBlock = block(BlockType.INFO);
        LessonBlockAttempt infoView =
                LessonBlockAttempt.builder()
                        .lessonBlock(infoBlock)
                        .isCorrect(null)
                        .attemptedAt(Instant.now())
                        .build();
        when(lessonBlockAttemptRepository.findByUserIdOrderByAttemptedAtDesc(userId))
                .thenReturn(List.of(infoView));
        when(practiceAttemptRepository.findByUserIdOrderByAttemptedAtDesc(userId))
                .thenReturn(List.of());

        List<PracticeMistakeResponse> result = practiceService.getMistakes(mockUser, 20);

        assertTrue(result.isEmpty());
    }

    @Test
    void getMistakeExercises_ShouldReturnFullBlocksForPendingMistakes() {
        LessonBlock pendingBlock = block(BlockType.MATCH);
        LessonBlockAttempt wrong =
                LessonBlockAttempt.builder()
                        .lessonBlock(pendingBlock)
                        .isCorrect(false)
                        .attemptedAt(Instant.now())
                        .build();
        when(lessonBlockAttemptRepository.findByUserIdOrderByAttemptedAtDesc(userId))
                .thenReturn(List.of(wrong));
        when(practiceAttemptRepository.findByUserIdOrderByAttemptedAtDesc(userId))
                .thenReturn(List.of());

        List<LessonBlockResponse> result = practiceService.getMistakeExercises(mockUser, 20);

        assertEquals(1, result.size());
        assertEquals(pendingBlock.getId(), result.get(0).id());
    }

    @Test
    void recordAttempt_ShouldSavePracticeAttempt_WithoutTouchingLessonProgress() {
        LessonBlock lessonBlock = block(BlockType.MATCH);
        when(lessonBlockRepository.findById(lessonBlock.getId()))
                .thenReturn(Optional.of(lessonBlock));

        practiceService.recordAttempt(mockUser, lessonBlock.getId(), true);

        verify(practiceAttemptRepository).save(any(PracticeAttempt.class));
        verify(lessonBlockAttemptRepository, never()).save(any());
    }

    @Test
    void recordAttempt_ShouldThrowNotFound_WhenBlockDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        when(lessonBlockRepository.findById(missingId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> practiceService.recordAttempt(mockUser, missingId, true));
    }

    @Test
    void getSummary_ShouldCombineLearnedSignsAndPracticeAttemptCounts() {
        UserStats stats = UserStats.builder().learnedSignsCount(7).build();
        when(userStatsRepository.findByUser(mockUser)).thenReturn(Optional.of(stats));
        when(practiceAttemptRepository.countByUserId(userId)).thenReturn(15L);

        PracticeSummaryResponse summary = practiceService.getSummary(mockUser);

        assertEquals(new PracticeSummaryResponse(7, 15L), summary);
    }

    @Test
    void getSummary_ShouldDefaultSignsLearnedToZero_WhenNoStatsYet() {
        when(userStatsRepository.findByUser(mockUser)).thenReturn(Optional.empty());
        when(practiceAttemptRepository.countByUserId(userId)).thenReturn(0L);

        PracticeSummaryResponse summary = practiceService.getSummary(mockUser);

        assertEquals(new PracticeSummaryResponse(0, 0L), summary);
    }

    @Test
    void completeMistakeReview_ShouldPublishXpEarnedEventAndReturnAwardedAmount() {
        int xpEarned = practiceService.completeMistakeReview(mockUser);

        ArgumentCaptor<XpEarnedEvent> eventCaptor = ArgumentCaptor.forClass(XpEarnedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(mockUser, eventCaptor.getValue().getUser());
        assertEquals(xpEarned, eventCaptor.getValue().getXpAmount());
        assertTrue(xpEarned > 0);
    }
}
