package com.signasource.signa_api.learning.service;

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
import com.signasource.signa_api.learning.entity.UserBlockProgress;
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
import java.time.Instant;
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
    private final UserBlockProgressRepository blockProgressRepository;
    private final ExerciseAttemptRepository attemptRepository;

    private final CourseVersionRepository courseVersionRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final LessonRepository lessonRepository;
    private final TopicRepository topicRepository;

    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public UserCourseEnrollment enrollUserInCourse(User user, UUID courseVersionId) {
        if (enrollmentRepository.existsByUserIdAndCourseVersionId(user.getId(), courseVersionId)) {
            throw new ResourceAlreadyInUseException(
                    "User is already enrolled in this course's version");
        }

        CourseVersion courseVersion =
                courseVersionRepository
                        .findById(courseVersionId)
                        .orElseThrow(() -> new NotFoundException("Course version not found"));

        UserCourseEnrollment enrollment = new UserCourseEnrollment();
        enrollment.setUser(user);
        enrollment.setCourseVersion(courseVersion);
        enrollment.setStatus(EnrollmentStatus.ENROLLED);

        return enrollmentRepository.save(enrollment);
    }

    /**
     * Single entry point for block progression. Exercise blocks always record the attempt and are
     * completed on the first correct answer; other block types (theory, video) are completed on the
     * first call without recording an attempt. Completing a block for the first time awards its XP
     * once and recomputes the status of the lesson, topic and course version in cascade.
     */
    @Transactional
    public void recordBlockProgress(User user, UUID lessonBlockId, Boolean isCorrect) {
        LessonBlock block =
                lessonBlockRepository
                        .findById(lessonBlockId)
                        .orElseThrow(() -> new NotFoundException("Lesson block not found"));

        Lesson lesson = block.getLesson();
        CourseVersion courseVersion = lesson.getTopic().getCourseVersion();

        if (!enrollmentRepository.existsByUserIdAndCourseVersionIdAndStatusNot(
                user.getId(), courseVersion.getId(), EnrollmentStatus.DROPPED)) {
            throw new InvalidInputException("User is not enrolled in this course version");
        }

        boolean firstCompletion;
        if (block.getType() == BlockType.EXERCISE_ATTEMPT) {
            if (isCorrect == null) {
                throw new InvalidInputException("isCorrect is required for exercise blocks");
            }
            attemptRepository.save(
                    ExerciseAttempt.builder()
                            .user(user)
                            .lessonBlock(block)
                            .isCorrect(isCorrect)
                            .build());
            firstCompletion = isCorrect && markBlockCompleted(user, block);
        } else {
            firstCompletion = markBlockCompleted(user, block);
        }

        if (firstCompletion) {
            if (block.getXpReward() > 0) {
                eventPublisher.publishEvent(new XpEarnedEvent(this, user, block.getXpReward()));
            }
            recomputeLessonStatus(user, lesson);
        }
    }

    /**
     * Persists the block completion if it is not yet recorded. The unique constraint on (user,
     * block) plus the same-transaction XP listener guarantee XP is never awarded twice under
     * concurrent completions of the same block: the losing transaction rolls back entirely.
     */
    private boolean markBlockCompleted(User user, LessonBlock block) {
        if (blockProgressRepository.existsByUserIdAndLessonBlockId(user.getId(), block.getId())) {
            return false;
        }
        blockProgressRepository.save(
                UserBlockProgress.builder().user(user).lessonBlock(block).build());
        return true;
    }

    private void recomputeLessonStatus(User user, Lesson lesson) {
        long total = lessonBlockRepository.countByLessonId(lesson.getId());
        long done =
                blockProgressRepository.countByUserIdAndLessonBlockLessonId(
                        user.getId(), lesson.getId());

        UserLessonProgress progress =
                lessonProgressRepository
                        .findByUserIdAndLessonId(user.getId(), lesson.getId())
                        .orElseGet(
                                () ->
                                        UserLessonProgress.builder()
                                                .user(user)
                                                .lesson(lesson)
                                                .build());

        if (progress.getStatus() == ProgressStatus.COMPLETED) {
            return;
        }

        if (total > 0 && done >= total) {
            progress.setStatus(ProgressStatus.COMPLETED);
            progress.setCompletedAt(Instant.now());
            progress.setXpEarned(lessonBlockRepository.sumXpRewardByLessonId(lesson.getId()));
            lessonProgressRepository.save(progress);
            recomputeTopicStatus(user, lesson.getTopic());
        } else {
            progress.setStatus(ProgressStatus.IN_PROGRESS);
            lessonProgressRepository.save(progress);
        }
    }

    private void recomputeTopicStatus(User user, Topic topic) {
        long total = lessonRepository.countByTopicId(topic.getId());
        long done =
                lessonProgressRepository.countByUserIdAndLessonTopicIdAndStatus(
                        user.getId(), topic.getId(), ProgressStatus.COMPLETED);

        UserTopicProgress progress =
                topicProgressRepository
                        .findByUserIdAndTopicId(user.getId(), topic.getId())
                        .orElseGet(
                                () -> UserTopicProgress.builder().user(user).topic(topic).build());

        if (progress.getStatus() == ProgressStatus.COMPLETED) {
            return;
        }

        if (progress.getStartedAt() == null) {
            progress.setStartedAt(Instant.now());
        }

        if (total > 0 && done >= total) {
            progress.setStatus(ProgressStatus.COMPLETED);
            progress.setCompletedAt(Instant.now());
            topicProgressRepository.save(progress);
            recomputeCourseStatus(user, topic.getCourseVersion());
        } else {
            progress.setStatus(ProgressStatus.IN_PROGRESS);
            topicProgressRepository.save(progress);
        }
    }

    private void recomputeCourseStatus(User user, CourseVersion courseVersion) {
        long total = topicRepository.countByCourseVersionId(courseVersion.getId());
        long done =
                topicProgressRepository.countByUserIdAndTopicCourseVersionIdAndStatus(
                        user.getId(), courseVersion.getId(), ProgressStatus.COMPLETED);

        if (total == 0 || done < total) {
            return;
        }

        enrollmentRepository
                .findByUserIdAndCourseVersionId(user.getId(), courseVersion.getId())
                .filter(enrollment -> enrollment.getStatus() != EnrollmentStatus.COMPLETED)
                .ifPresent(
                        enrollment -> {
                            enrollment.setStatus(EnrollmentStatus.COMPLETED);
                            enrollment.setCompletedAt(Instant.now());
                            enrollmentRepository.save(enrollment);
                        });
    }
}
