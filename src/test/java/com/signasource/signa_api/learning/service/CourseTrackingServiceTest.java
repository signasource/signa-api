package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.ExerciseAttempt;
import com.signasource.signa_api.learning.entity.Lesson;
import com.signasource.signa_api.learning.entity.LessonBlock;
import com.signasource.signa_api.learning.entity.ProgressStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.UserLessonProgress;
import com.signasource.signa_api.learning.event.XpEarnedEvent;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.ExerciseAttemptRepository;
import com.signasource.signa_api.learning.repository.LessonBlockRepository;
import com.signasource.signa_api.learning.repository.LessonRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class CourseTrackingServiceTest {

    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private UserLessonProgressRepository lessonProgressRepository;
    @Mock private ExerciseAttemptRepository attemptRepository;
    @Mock private UserRepository userRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private LessonRepository lessonRepository;
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
        when(userRepository.getReferenceById(userId)).thenReturn(mockUser);
        when(courseVersionRepository.getReferenceById(courseVersionId)).thenReturn(mockVersion);

        UserCourseEnrollment savedEnrollment = new UserCourseEnrollment();
        savedEnrollment.setStatus(EnrollmentStatus.ENROLLED);
        when(enrollmentRepository.save(any(UserCourseEnrollment.class)))
                .thenReturn(savedEnrollment);

        UserCourseEnrollment result =
                courseTrackingService.enrollUserInCourse(userId, courseVersionId);

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
                () -> courseTrackingService.enrollUserInCourse(userId, courseVersionId));
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void completeLesson_CreatesNewProgress_AndPublishesEvent() {
        UUID lessonId = UUID.randomUUID();
        Integer xpEarned = 50;

        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId))
                .thenReturn(Optional.empty());
        when(userRepository.getReferenceById(userId)).thenReturn(mockUser);
        when(lessonRepository.getReferenceById(lessonId)).thenReturn(new Lesson());

        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        UserLessonProgress result =
                courseTrackingService.completeLesson(userId, lessonId, xpEarned);

        assertEquals(ProgressStatus.COMPLETED, result.getStatus());
        assertEquals(xpEarned, result.getXpEarned());
        assertNotNull(result.getCompletedAt());

        verify(eventPublisher).publishEvent(any(XpEarnedEvent.class));
    }

    @Test
    void completeLesson_UpdatesExistingProgress_NoXpEventIfZero() {
        UUID lessonId = UUID.randomUUID();
        UserLessonProgress existingProgress =
                UserLessonProgress.builder()
                        .status(ProgressStatus.IN_PROGRESS)
                        .xpEarned(10)
                        .build();

        when(lessonProgressRepository.findByUserIdAndLessonId(userId, lessonId))
                .thenReturn(Optional.of(existingProgress));
        when(lessonProgressRepository.save(any(UserLessonProgress.class)))
                .thenReturn(existingProgress);

        UserLessonProgress result = courseTrackingService.completeLesson(userId, lessonId, 0);

        assertEquals(ProgressStatus.COMPLETED, result.getStatus());
        assertEquals(10, result.getXpEarned());

        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void recordExerciseAttempt_Success() {
        UUID blockId = UUID.randomUUID();
        when(userRepository.getReferenceById(userId)).thenReturn(mockUser);
        when(lessonBlockRepository.getReferenceById(blockId)).thenReturn(new LessonBlock());

        ExerciseAttempt savedAttempt = ExerciseAttempt.builder().isCorrect(true).build();
        when(attemptRepository.save(any(ExerciseAttempt.class))).thenReturn(savedAttempt);

        ExerciseAttempt result = courseTrackingService.recordExerciseAttempt(userId, blockId, true);

        assertTrue(result.getIsCorrect());
        verify(attemptRepository).save(any(ExerciseAttempt.class));
    }
}
