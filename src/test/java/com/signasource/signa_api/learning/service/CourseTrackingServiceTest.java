package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.gamification.repository.UserLearnedSignRepository;
import com.signasource.signa_api.learning.dto.CourseProgressResponse;
import com.signasource.signa_api.learning.dto.TopicProgressResponse;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.LessonBlockAttempt;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.UserLessonProgress;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.learning.event.LifeLostEvent;
import com.signasource.signa_api.learning.event.SignsLearnedEvent;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.LessonBlockAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
import com.signasource.signa_api.learning.repository.projection.TopicCompletedCountView;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import com.signasource.signa_api.learning.util.BlockSignExtractor;
import com.signasource.signa_api.users.entity.User;
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
class CourseTrackingServiceTest {

    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserTopicProgressRepository topicProgressRepository;
    @Mock private UserLessonProgressRepository lessonProgressRepository;
    @Mock private LessonBlockAttemptRepository attemptRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private TopicRepository topicRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private BlockSignExtractor blockSignExtractor;
    @Mock private UserLearnedSignRepository userLearnedSignRepository;

    @InjectMocks private CourseTrackingService courseTrackingService;

    private UUID userId;
    private UUID courseVersionId;
    private User mockUser;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        courseVersionId = UUID.randomUUID();
        mockUser = new User();
        mockUser.setId(userId);
    }

    @Test
    void enrollUserInCourse_Success() {
        CourseVersion mockVersion = new CourseVersion();
        when(enrollmentRepository.existsByUserIdAndCourseVersionId(userId, courseVersionId))
                .thenReturn(false);
        when(courseVersionRepository.findById(courseVersionId))
                .thenReturn(Optional.of(mockVersion));

        UserCourseEnrollment savedEnrollment = new UserCourseEnrollment();
        savedEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        when(enrollmentRepository.save(any(UserCourseEnrollment.class)))
                .thenReturn(savedEnrollment);

        UserCourseEnrollment result =
                courseTrackingService.enrollUserInCourse(mockUser, courseVersionId);

        assertNotNull(result);
        assertEquals(EnrollmentStatus.ENROLLED, result.getStatus());
        verify(enrollmentRepository).save(any(UserCourseEnrollment.class));
    }

    @Test
    void enrollUserInCourse_ThrowsException_WhenAlreadyEnrolled() {
        when(enrollmentRepository.existsByUserIdAndCourseVersionId(userId, courseVersionId))
                .thenReturn(true);

        assertThrows(
                ResourceAlreadyInUseException.class,
                () -> courseTrackingService.enrollUserInCourse(mockUser, courseVersionId));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void enrollUserInCourse_ThrowsNotFound_WhenCourseVersionMissing() {
        when(enrollmentRepository.existsByUserIdAndCourseVersionId(userId, courseVersionId))
                .thenReturn(false);
        when(courseVersionRepository.findById(courseVersionId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> courseTrackingService.enrollUserInCourse(mockUser, courseVersionId));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void recordBlockInteraction_ThrowsNotFound_WhenBlockMissing() {
        UUID blockId = UUID.randomUUID();
        when(lessonBlockRepository.findWithCourseVersionById(blockId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> courseTrackingService.recordBlockInteraction(mockUser, blockId, true));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordBlockInteraction_ThrowsInvalidInput_WhenInfoBlockHasCorrectnessValue() {
        UUID blockId = UUID.randomUUID();
        LessonBlock infoBlock = LessonBlock.builder().id(blockId).type(BlockType.INFO).build();
        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(infoBlock));

        assertThrows(
                InvalidInputException.class,
                () -> courseTrackingService.recordBlockInteraction(mockUser, blockId, true));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordBlockInteraction_ThrowsInvalidInput_WhenEvaluableBlockHasNoCorrectnessValue() {
        UUID blockId = UUID.randomUUID();
        LessonBlock exerciseBlock =
                LessonBlock.builder().id(blockId).type(BlockType.SELECT_MEANING).build();
        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(exerciseBlock));

        assertThrows(
                InvalidInputException.class,
                () -> courseTrackingService.recordBlockInteraction(mockUser, blockId, null));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordBlockInteraction_IncorrectAttempt_DoesNotAwardXp_ButMarksLessonAndTopicInProgress() {
        UUID blockId = UUID.randomUUID();
        Topic topic = Topic.builder().id(UUID.randomUUID()).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(topicProgressRepository.findByUserIdAndTopicId(userId, topic.getId()))
                .thenReturn(Optional.empty());
        when(topicProgressRepository.save(any(UserTopicProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonBlockAttempt result =
                courseTrackingService.recordBlockInteraction(mockUser, blockId, false);

        assertFalse(result.getIsCorrect());
        verify(eventPublisher, never()).publishEvent(any(XpEarnedEvent.class));
        verify(eventPublisher).publishEvent(any(LifeLostEvent.class));

        ArgumentCaptor<UserLessonProgress> lessonCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository).save(lessonCaptor.capture());
        assertEquals(ProgressStatus.IN_PROGRESS, lessonCaptor.getValue().getStatus());
        assertNotNull(lessonCaptor.getValue().getStartedAt());

        ArgumentCaptor<UserTopicProgress> topicCaptor =
                ArgumentCaptor.forClass(UserTopicProgress.class);
        verify(topicProgressRepository).save(topicCaptor.capture());
        assertEquals(ProgressStatus.IN_PROGRESS, topicCaptor.getValue().getStatus());
    }

    @Test
    void recordBlockInteraction_DoesNotRegressCompletedLessonBackToInProgress() {
        UUID blockId = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.COMPLETED)
                                        .build()));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(lessonProgressRepository, never()).save(any());
        verify(topicProgressRepository, never()).findByUserIdAndTopicId(any(), any());
    }

    @Test
    void recordBlockInteraction_RepeatedCorrectAttempt_DoesNotAwardXpAgain() {
        UUID blockId = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordBlockInteraction_FirstCorrectAttempt_CompletesLessonOnly_WhenTopicHasOtherLessons() {
        UUID blockId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();
        UUID otherLessonId = UUID.randomUUID();

        Topic topic = Topic.builder().id(topicId).build();
        Lesson lesson = Lesson.builder().id(lessonId).topic(topic).build();
        Lesson otherLesson = Lesson.builder().id(otherLessonId).topic(topic).build();
        topic.setLessons(List.of(lesson, otherLesson));

        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(block));

        when(blockSignExtractor.extract(block)).thenReturn(List.of());
        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonTopicId(userId, topicId))
                .thenReturn(
                        List.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.COMPLETED)
                                        .build()));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));

        ArgumentCaptor<UserLessonProgress> progressCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository).save(progressCaptor.capture());
        assertEquals(ProgressStatus.COMPLETED, progressCaptor.getValue().getStatus());
        assertEquals(50, progressCaptor.getValue().getXpEarned());

        verify(topicProgressRepository, never()).save(any());
    }

    @Test
    void recordBlockInteraction_CascadesUpToCourseCompletion_WhenSingleLessonAndTopic() {
        UUID blockId = UUID.randomUUID();
        UUID lessonId = UUID.randomUUID();
        UUID topicId = UUID.randomUUID();

        CourseVersion courseVersion = CourseVersion.builder().id(courseVersionId).build();
        Topic topic = Topic.builder().id(topicId).courseVersion(courseVersion).build();
        courseVersion.setTopics(List.of(topic));

        Lesson lesson = Lesson.builder().id(lessonId).topic(topic).build();
        topic.setLessons(List.of(lesson));

        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(block));

        when(blockSignExtractor.extract(block)).thenReturn(List.of());
        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonTopicId(userId, topicId))
                .thenReturn(
                        List.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.COMPLETED)
                                        .build()));
        when(topicProgressRepository.findByUserIdAndTopicId(userId, topicId))
                .thenReturn(Optional.empty());
        when(topicProgressRepository.save(any(UserTopicProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(topicProgressRepository.findByUserIdAndTopicCourseVersionId(userId, courseVersionId))
                .thenReturn(
                        List.of(
                                UserTopicProgress.builder()
                                        .topic(topic)
                                        .status(ProgressStatus.COMPLETED)
                                        .build()));

        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        when(enrollmentRepository.findByUserIdAndCourseVersionId(userId, courseVersionId))
                .thenReturn(Optional.of(enrollment));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(lessonProgressRepository).save(any(UserLessonProgress.class));
        verify(topicProgressRepository).save(any(UserTopicProgress.class));
        verify(enrollmentRepository).save(enrollment);
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertNotNull(enrollment.getCompletedAt());
    }

    @Test
    void recordBlockInteraction_DoesNotCompleteLesson_WhenOtherBlocksPending() {
        UUID blockId = UUID.randomUUID();
        UUID otherBlockId = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();
        LessonBlock otherBlock =
                LessonBlock.builder()
                        .id(otherBlockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(block, otherBlock));

        when(blockSignExtractor.extract(block)).thenReturn(List.of());
        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, otherBlockId))
                .thenReturn(false);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));
        verify(lessonProgressRepository, never()).save(any());
    }

    @Test
    void recordBlockInteraction_FirstView_AwardsXpAndMarksLessonInProgress() {
        UUID blockId = UUID.randomUUID();
        Topic topic = Topic.builder().id(UUID.randomUUID()).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.INFO)
                        .xpReward(20)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(block));

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockId(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(topicProgressRepository.findByUserIdAndTopicId(userId, topic.getId()))
                .thenReturn(Optional.empty());
        when(topicProgressRepository.save(any(UserTopicProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        LessonBlockAttempt result =
                courseTrackingService.recordBlockInteraction(mockUser, blockId, null);

        assertNotNull(result);
        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));

        ArgumentCaptor<UserLessonProgress> progressCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository, atLeastOnce()).save(progressCaptor.capture());
        assertEquals(ProgressStatus.COMPLETED, progressCaptor.getValue().getStatus());
        assertEquals(20, progressCaptor.getValue().getXpEarned());
    }

    @Test
    void recordBlockInteraction_FirstView_NoXpEvent_WhenBlockHasNoXpReward() {
        UUID blockId = UUID.randomUUID();
        Topic topic = Topic.builder().id(UUID.randomUUID()).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        LessonBlock block =
                LessonBlock.builder().id(blockId).type(BlockType.INFO).lesson(lesson).build();
        lesson.setLessonBlocks(List.of(block));

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockId(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(topicProgressRepository.findByUserIdAndTopicId(userId, topic.getId()))
                .thenReturn(Optional.empty());
        when(topicProgressRepository.save(any(UserTopicProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordBlockInteraction_RepeatedView_DoesNotAwardXpAgain() {
        UUID blockId = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.INFO)
                        .xpReward(20)
                        .lesson(lesson)
                        .build();

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockId(userId, blockId)).thenReturn(true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, null);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordBlockInteraction_CompletesLesson_WhenLastPendingBlockAmongMixedTypes() {
        UUID infoBlockId = UUID.randomUUID();
        UUID exerciseBlockId = UUID.randomUUID();
        Topic topic = Topic.builder().id(UUID.randomUUID()).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        LessonBlock infoBlock =
                LessonBlock.builder()
                        .id(infoBlockId)
                        .type(BlockType.INFO)
                        .xpReward(10)
                        .lesson(lesson)
                        .build();
        LessonBlock exerciseBlock =
                LessonBlock.builder()
                        .id(exerciseBlockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(infoBlock, exerciseBlock));

        when(lessonBlockRepository.findWithCourseVersionById(infoBlockId))
                .thenReturn(Optional.of(infoBlock));
        when(attemptRepository.existsByUserIdAndLessonBlockId(userId, infoBlockId))
                .thenReturn(false, true);
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(
                        userId, exerciseBlockId))
                .thenReturn(true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        courseTrackingService.recordBlockInteraction(mockUser, infoBlockId, null);

        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));

        ArgumentCaptor<UserLessonProgress> progressCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository).save(progressCaptor.capture());
        assertEquals(ProgressStatus.COMPLETED, progressCaptor.getValue().getStatus());
        assertEquals(60, progressCaptor.getValue().getXpEarned());
    }

    @Test
    void shouldPublishSignsLearnedEvent_WhenFirstCorrectAttemptExtractsSigns() {
        UUID blockId = UUID.randomUUID();
        Topic topic = Topic.builder().id(UUID.randomUUID()).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(10)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(block));

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));
        when(blockSignExtractor.extract(block)).thenReturn(List.of("hola"));

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(eventPublisher, times(1)).publishEvent(any(XpEarnedEvent.class));
        verify(eventPublisher, times(1)).publishEvent(any(SignsLearnedEvent.class));
    }

    @Test
    void shouldNotPublishSignsLearnedEvent_WhenBlockExtractsNoSigns() {
        UUID blockId = UUID.randomUUID();
        Topic topic = Topic.builder().id(UUID.randomUUID()).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(10)
                        .lesson(lesson)
                        .build();
        lesson.setLessonBlocks(List.of(block));

        when(lessonBlockRepository.findWithCourseVersionById(blockId))
                .thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(LessonBlockAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(
                        Optional.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.IN_PROGRESS)
                                        .build()));
        when(blockSignExtractor.extract(block)).thenReturn(List.of());

        courseTrackingService.recordBlockInteraction(mockUser, blockId, true);

        verify(eventPublisher, times(1)).publishEvent(any(XpEarnedEvent.class));
        verify(eventPublisher, times(0)).publishEvent(any(SignsLearnedEvent.class));
    }

    @Test
    void getUserCourseProgress_ReturnsEmptyList_WhenUserHasNoEnrollments() {
        when(enrollmentRepository.findWithCourseByUserId(userId)).thenReturn(List.of());

        List<CourseProgressResponse> result = courseTrackingService.getUserCourseProgress(mockUser);

        assertTrue(result.isEmpty());
        verifyNoInteractions(topicRepository);
    }

    @Test
    void getUserCourseProgress_AggregatesLessonCountsAndReportsOnlyTheInProgressTopic() {
        UUID versionWithTopics = UUID.randomUUID();
        UUID versionWithoutTopics = UUID.randomUUID();
        UUID greetingsTopic = UUID.randomUUID();
        UUID numbersTopic = UUID.randomUUID();
        UUID emptyTopic = UUID.randomUUID();

        CourseVersion courseVersion =
                CourseVersion.builder()
                        .id(versionWithTopics)
                        .course(Course.builder().name("Basic LSA").build())
                        .build();
        CourseVersion emptyCourseVersion =
                CourseVersion.builder()
                        .id(versionWithoutTopics)
                        .course(Course.builder().name("Empty course").build())
                        .build();
        List<UserCourseEnrollment> enrollments =
                List.of(
                        UserCourseEnrollment.builder()
                                .courseVersion(courseVersion)
                                .status(EnrollmentStatus.ENROLLED)
                                .build(),
                        UserCourseEnrollment.builder()
                                .courseVersion(emptyCourseVersion)
                                .status(EnrollmentStatus.DROPPED)
                                .build());

        Topic numbers =
                Topic.builder()
                        .id(numbersTopic)
                        .name("Numbers")
                        .courseVersion(courseVersion)
                        .build();
        List<UserTopicProgress> inProgressTopics =
                List.of(
                        UserTopicProgress.builder()
                                .topic(numbers)
                                .status(ProgressStatus.IN_PROGRESS)
                                .build());

        List<TopicCompletedCountView> completedCounts =
                List.of(completedView(greetingsTopic, 4L), completedView(numbersTopic, 1L));
        List<TopicLessonTotalView> topicTotals =
                List.of(
                        topicTotalView(versionWithTopics, greetingsTopic, 4L),
                        topicTotalView(versionWithTopics, numbersTopic, 2L),
                        topicTotalView(versionWithTopics, emptyTopic, 0L));

        when(enrollmentRepository.findWithCourseByUserId(userId)).thenReturn(enrollments);
        when(lessonProgressRepository.findCompletedLessonCountsByTopic(eq(userId), anyCollection()))
                .thenReturn(completedCounts);
        when(topicRepository.findTopicLessonTotals(anyCollection())).thenReturn(topicTotals);
        when(topicProgressRepository.findInProgressTopics(eq(userId), anyCollection()))
                .thenReturn(inProgressTopics);

        List<CourseProgressResponse> result = courseTrackingService.getUserCourseProgress(mockUser);

        assertEquals(2, result.size());

        CourseProgressResponse course = result.get(0);
        assertEquals("Basic LSA", course.courseName());
        assertEquals(EnrollmentStatus.ENROLLED, course.status());
        assertEquals(6, course.totalLessons());
        assertEquals(5, course.completedLessons());
        assertEquals(83, course.progressPercentage());
        assertEquals(0, course.signsLearned());

        TopicProgressResponse currentTopic = course.currentTopic();
        assertNotNull(currentTopic);
        assertEquals("Numbers", currentTopic.title());
        assertEquals(2, currentTopic.totalLessons());
        assertEquals(1, currentTopic.completedLessons());
        assertEquals(50, currentTopic.progressPercentage());

        CourseProgressResponse courseWithoutTopics = result.get(1);
        assertEquals(EnrollmentStatus.DROPPED, courseWithoutTopics.status());
        assertEquals(0, courseWithoutTopics.totalLessons());
        assertEquals(0, courseWithoutTopics.completedLessons());
        assertEquals(0, courseWithoutTopics.progressPercentage());
        assertNull(courseWithoutTopics.currentTopic());
    }

    private static TopicLessonTotalView topicTotalView(
            UUID courseVersionId, UUID topicId, long totalLessons) {
        TopicLessonTotalView view = mock(TopicLessonTotalView.class);
        when(view.getCourseVersionId()).thenReturn(courseVersionId);
        when(view.getTopicId()).thenReturn(topicId);
        when(view.getTotalLessons()).thenReturn(totalLessons);
        return view;
    }

    private static TopicCompletedCountView completedView(UUID topicId, long completedLessons) {
        TopicCompletedCountView view = mock(TopicCompletedCountView.class);
        when(view.getTopicId()).thenReturn(topicId);
        when(view.getCompletedLessons()).thenReturn(completedLessons);
        return view;
    }
}
