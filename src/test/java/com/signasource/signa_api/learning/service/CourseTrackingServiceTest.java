package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.BlockType;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.ExerciseAttempt;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.UserLessonProgress;
import com.signasource.signa_api.learning.entity.UserTopicProgress;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.ExerciseAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
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
    @Mock private ExerciseAttemptRepository attemptRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private ApplicationEventPublisher eventPublisher;

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
    void recordExerciseAttempt_ThrowsNotFound_WhenBlockMissing() {
        UUID blockId = UUID.randomUUID();
        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> courseTrackingService.recordExerciseAttempt(mockUser, blockId, true));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordExerciseAttempt_ThrowsInvalidInput_WhenBlockIsNotExercise() {
        UUID blockId = UUID.randomUUID();
        LessonBlock theoryBlock = LessonBlock.builder().id(blockId).type(BlockType.INFO).build();
        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.of(theoryBlock));

        assertThrows(
                InvalidInputException.class,
                () -> courseTrackingService.recordExerciseAttempt(mockUser, blockId, true));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordExerciseAttempt_IncorrectAttempt_DoesNotAwardXp() {
        UUID blockId = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();

        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(attemptRepository.save(any(ExerciseAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        ExerciseAttempt result =
                courseTrackingService.recordExerciseAttempt(mockUser, blockId, false);

        assertFalse(result.getIsCorrect());
        verify(eventPublisher, never()).publishEvent(any());
        verify(lessonProgressRepository, never()).findByUserIdAndLessonId(any(), any());
    }

    @Test
    void recordExerciseAttempt_RepeatedCorrectAttempt_DoesNotAwardXpAgain() {
        UUID blockId = UUID.randomUUID();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).build();
        LessonBlock block =
                LessonBlock.builder()
                        .id(blockId)
                        .type(BlockType.SELECT_MEANING)
                        .xpReward(50)
                        .lesson(lesson)
                        .build();

        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(true);
        when(attemptRepository.save(any(ExerciseAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        courseTrackingService.recordExerciseAttempt(mockUser, blockId, true);

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordExerciseAttempt_FirstCorrectAttempt_CompletesLessonOnly_WhenTopicHasOtherLessons() {
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

        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(ExerciseAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId))
                .thenReturn(Optional.empty());
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonTopicId(userId, topicId))
                .thenReturn(
                        List.of(
                                UserLessonProgress.builder()
                                        .lesson(lesson)
                                        .status(ProgressStatus.COMPLETED)
                                        .build()));

        courseTrackingService.recordExerciseAttempt(mockUser, blockId, true);

        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));

        ArgumentCaptor<UserLessonProgress> progressCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository).save(progressCaptor.capture());
        assertEquals(ProgressStatus.COMPLETED, progressCaptor.getValue().getStatus());
        assertEquals(50, progressCaptor.getValue().getXpEarned());

        verify(topicProgressRepository, never()).save(any());
    }

    @Test
    void recordExerciseAttempt_CascadesUpToCourseCompletion_WhenSingleLessonAndTopic() {
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

        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.save(any(ExerciseAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId))
                .thenReturn(Optional.empty());
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

        courseTrackingService.recordExerciseAttempt(mockUser, blockId, true);

        verify(lessonProgressRepository).save(any(UserLessonProgress.class));
        verify(topicProgressRepository).save(any(UserTopicProgress.class));
        verify(enrollmentRepository).save(enrollment);
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertNotNull(enrollment.getCompletedAt());
    }

    @Test
    void recordExerciseAttempt_DoesNotCompleteLesson_WhenOtherBlocksPending() {
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

        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.of(block));
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, blockId))
                .thenReturn(false, true);
        when(attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(userId, otherBlockId))
                .thenReturn(false);
        when(attemptRepository.save(any(ExerciseAttempt.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        courseTrackingService.recordExerciseAttempt(mockUser, blockId, true);

        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));
        verify(lessonProgressRepository, never()).save(any());
    }
}
