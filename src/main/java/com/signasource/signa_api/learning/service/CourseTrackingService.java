package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
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
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.UserTopicProgressRepository;
import com.signasource.signa_api.users.entity.User;
import com.signasource.signa_api.users.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CourseTrackingService {

    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserTopicProgressRepository topicProgressRepository;
    private final UserLessonProgressRepository lessonProgressRepository;
    private final ExerciseAttemptRepository attemptRepository;

    private final UserRepository userRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final TopicRepository topicRepository;
    private final LessonRepository lessonRepository;
    private final LessonBlockRepository lessonBlockRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserCourseEnrollment enrollUserInCourse(UUID userId, UUID courseVersionId) {
        if (enrollmentRepository.existsByUserIdAndCourseVersionId(userId, courseVersionId)) {
            throw new ResourceAlreadyInUseException(
                    "User is already enrolled in this course's version");
        }

        User userProxy = userRepository.getReferenceById(userId);
        CourseVersion courseVersionProxy =
                courseVersionRepository.getReferenceById(courseVersionId);

        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setUser(userProxy);
        enrollment.setCourseVersion(courseVersionProxy);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);

        return enrollmentRepository.save(enrollment);
    }

    @Transactional
    public UserLessonProgress completeLesson(UUID userId, UUID lessonId, Integer xpEarned) {
        UserLessonProgress progress =
                lessonProgressRepository
                        .findByUserIdAndLessonId(userId, lessonId)
                        .orElseGet(
                                () -> {
                                    User userProxy = userRepository.getReferenceById(userId);
                                    Lesson lessonProxy =
                                            lessonRepository.getReferenceById(lessonId);

                                    return UserLessonProgress.builder()
                                            .user(userProxy)
                                            .lesson(lessonProxy)
                                            .status(ProgressStatus.IN_PROGRESS)
                                            .xpEarned(0)
                                            .build();
                                });

        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setCompletedAt(LocalDateTime.now());
        progress.setXpEarned(progress.getXpEarned() + xpEarned);

        lessonProgressRepository.save(progress);

        if (xpEarned > 0) {
            eventPublisher.publishEvent(new XpEarnedEvent(this, userId, xpEarned));
        }

        return progress;
    }

    @Transactional
    public ExerciseAttempt recordExerciseAttempt(
            UUID userId, UUID lessonBlockId, boolean isCorrect) {
        User userProxy = userRepository.getReferenceById(userId);
        LessonBlock blockProxy = lessonBlockRepository.getReferenceById(lessonBlockId);

        ExerciseAttempt attempt =
                ExerciseAttempt.builder()
                        .user(userProxy)
                        .lessonBlock(blockProxy)
                        .isCorrect(isCorrect)
                        .build();

        return attemptRepository.save(attempt);
    }

    @Transactional
    public UserTopicProgress updateTopicStatus(
            UUID userId, UUID topicId, ProgressStatus newStatus) {
        UserTopicProgress topicProgress =
                topicProgressRepository
                        .findByUserIdAndTopicId(userId, topicId)
                        .orElseGet(
                                () -> {
                                    User userProxy = userRepository.getReferenceById(userId);
                                    Topic topicProxy = topicRepository.getReferenceById(topicId);

                                    return UserTopicProgress.builder()
                                            .user(userProxy)
                                            .topic(topicProxy)
                                            .build();
                                });

        topicProgress.setStatus(newStatus);

        if (newStatus == ProgressStatus.COMPLETED && topicProgress.getCompletedAt() == null) {
            topicProgress.setCompletedAt(LocalDateTime.now());
        }

        return topicProgressRepository.save(topicProgress);
    }
}
