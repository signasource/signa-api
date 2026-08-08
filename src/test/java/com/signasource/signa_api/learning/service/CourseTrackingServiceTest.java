package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserBlockProgressRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
import com.signasource.signa_api.users.entity.User;
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
    @Mock private UserBlockProgressRepository blockProgressRepository;
    @Mock private ExerciseAttemptRepository attemptRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private LessonBlockRepository lessonBlockRepository;
    @Mock private LessonRepository lessonRepository;
    @Mock private TopicRepository topicRepository;
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

    private LessonBlock buildBlock(BlockType type, int xpReward, Lesson lesson) {
        return LessonBlock.builder()
                .id(UUID.randomUUID())
                .type(type)
                .xpReward(xpReward)
                .lesson(lesson)
                .build();
    }

    /**
     * Single block / single lesson / single topic hierarchy attached to {@code courseVersionId}.
     */
    private LessonBlock buildSingleBlockHierarchy(BlockType type, int xpReward) {
        CourseVersion courseVersion = CourseVersion.builder().id(courseVersionId).build();
        Topic topic = Topic.builder().id(UUID.randomUUID()).courseVersion(courseVersion).build();
        Lesson lesson = Lesson.builder().id(UUID.randomUUID()).topic(topic).build();
        return buildBlock(type, xpReward, lesson);
    }

    private void enrollmentActive() {
        when(enrollmentRepository.existsByUserIdAndCourseVersionIdAndStatusNot(
                        userId, courseVersionId, EnrollmentStatus.DROPPED))
                .thenReturn(true);
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
    void recordBlockProgress_ThrowsNotFound_WhenBlockMissing() {
        UUID blockId = UUID.randomUUID();
        when(lessonBlockRepository.findById(blockId)).thenReturn(Optional.empty());

        assertThrows(
                NotFoundException.class,
                () -> courseTrackingService.recordBlockProgress(mockUser, blockId, true));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordBlockProgress_ThrowsInvalidInput_WhenNotEnrolled() {
        LessonBlock block = buildSingleBlockHierarchy(BlockType.EXERCISE_ATTEMPT, 50);
        when(lessonBlockRepository.findById(block.getId())).thenReturn(Optional.of(block));
        when(enrollmentRepository.existsByUserIdAndCourseVersionIdAndStatusNot(
                        userId, courseVersionId, EnrollmentStatus.DROPPED))
                .thenReturn(false);

        assertThrows(
                InvalidInputException.class,
                () -> courseTrackingService.recordBlockProgress(mockUser, block.getId(), true));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordBlockProgress_ThrowsInvalidInput_WhenExerciseAndIsCorrectNull() {
        LessonBlock block = buildSingleBlockHierarchy(BlockType.EXERCISE_ATTEMPT, 50);
        when(lessonBlockRepository.findById(block.getId())).thenReturn(Optional.of(block));
        enrollmentActive();

        assertThrows(
                InvalidInputException.class,
                () -> courseTrackingService.recordBlockProgress(mockUser, block.getId(), null));
        verify(attemptRepository, never()).save(any());
    }

    @Test
    void recordBlockProgress_IncorrectExercise_RecordsAttempt_NoXp_NoCompletion() {
        LessonBlock block = buildSingleBlockHierarchy(BlockType.EXERCISE_ATTEMPT, 50);
        when(lessonBlockRepository.findById(block.getId())).thenReturn(Optional.of(block));
        enrollmentActive();

        courseTrackingService.recordBlockProgress(mockUser, block.getId(), false);

        verify(attemptRepository).save(any(ExerciseAttempt.class));
        verify(blockProgressRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
        verify(lessonProgressRepository, never()).save(any());
    }

    @Test
    void recordBlockProgress_RepeatedCorrect_DoesNotAwardXpAgain() {
        LessonBlock block = buildSingleBlockHierarchy(BlockType.EXERCISE_ATTEMPT, 50);
        when(lessonBlockRepository.findById(block.getId())).thenReturn(Optional.of(block));
        enrollmentActive();
        when(blockProgressRepository.existsByUserIdAndLessonBlockId(userId, block.getId()))
                .thenReturn(true);

        courseTrackingService.recordBlockProgress(mockUser, block.getId(), true);

        verify(attemptRepository).save(any(ExerciseAttempt.class));
        verify(blockProgressRepository, never()).save(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordBlockProgress_TheoryBlock_CompletesWithoutRecordingAttempt() {
        LessonBlock block = buildSingleBlockHierarchy(BlockType.THEORY, 20);
        Lesson lesson = block.getLesson();
        Topic topic = lesson.getTopic();

        when(lessonBlockRepository.findById(block.getId())).thenReturn(Optional.of(block));
        enrollmentActive();
        when(blockProgressRepository.existsByUserIdAndLessonBlockId(userId, block.getId()))
                .thenReturn(false);
        // Cascade: single block lesson, single lesson topic, single topic course -> all complete.
        when(lessonBlockRepository.countByLessonId(lesson.getId())).thenReturn(1L);
        when(blockProgressRepository.countByUserIdAndLessonBlockLessonId(userId, lesson.getId()))
                .thenReturn(1L);
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(Optional.empty());
        when(lessonBlockRepository.sumXpRewardByLessonId(lesson.getId())).thenReturn(20);
        when(lessonRepository.countByTopicId(topic.getId())).thenReturn(1L);
        when(lessonProgressRepository.countByUserIdAndLessonTopicIdAndStatus(
                        userId, topic.getId(), ProgressStatus.COMPLETED))
                .thenReturn(1L);
        when(topicProgressRepository.findByUserIdAndTopicId(userId, topic.getId()))
                .thenReturn(Optional.empty());
        when(topicRepository.countByCourseVersionId(courseVersionId)).thenReturn(1L);
        when(topicProgressRepository.countByUserIdAndTopicCourseVersionIdAndStatus(
                        userId, courseVersionId, ProgressStatus.COMPLETED))
                .thenReturn(1L);
        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setStatus(EnrollmentStatus.ENROLLED);
        when(enrollmentRepository.findByUserIdAndCourseVersionId(userId, courseVersionId))
                .thenReturn(Optional.of(enrollment));

        courseTrackingService.recordBlockProgress(mockUser, block.getId(), null);

        verify(attemptRepository, never()).save(any());
        verify(blockProgressRepository).save(any());
        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));

        ArgumentCaptor<UserLessonProgress> lessonCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository).save(lessonCaptor.capture());
        assertEquals(ProgressStatus.COMPLETED, lessonCaptor.getValue().getStatus());
        assertEquals(20, lessonCaptor.getValue().getXpEarned());

        verify(topicProgressRepository).save(any(UserTopicProgress.class));
        verify(enrollmentRepository).save(enrollment);
        assertEquals(EnrollmentStatus.COMPLETED, enrollment.getStatus());
        assertNotNull(enrollment.getCompletedAt());
    }

    @Test
    void recordBlockProgress_FirstCorrect_MarksLessonInProgress_WhenBlocksPending() {
        LessonBlock block = buildSingleBlockHierarchy(BlockType.EXERCISE_ATTEMPT, 50);
        Lesson lesson = block.getLesson();

        when(lessonBlockRepository.findById(block.getId())).thenReturn(Optional.of(block));
        enrollmentActive();
        when(blockProgressRepository.existsByUserIdAndLessonBlockId(userId, block.getId()))
                .thenReturn(false);
        when(lessonBlockRepository.countByLessonId(lesson.getId())).thenReturn(2L);
        when(blockProgressRepository.countByUserIdAndLessonBlockLessonId(userId, lesson.getId()))
                .thenReturn(1L);
        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lesson.getId()))
                .thenReturn(Optional.empty());

        courseTrackingService.recordBlockProgress(mockUser, block.getId(), true);

        verify(attemptRepository).save(any(ExerciseAttempt.class));
        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));

        ArgumentCaptor<UserLessonProgress> lessonCaptor =
                ArgumentCaptor.forClass(UserLessonProgress.class);
        verify(lessonProgressRepository).save(lessonCaptor.capture());
        assertEquals(ProgressStatus.IN_PROGRESS, lessonCaptor.getValue().getStatus());

        verify(topicProgressRepository, never()).save(any());
    }
}
