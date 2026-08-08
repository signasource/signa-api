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
import java.time.Instant;
import java.util.List;
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

    private final CourseVersionRepository courseVersionRepository;
    private final LessonBlockRepository lessonBlockRepository;

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
     * Records an attempt on an exercise block and cascades completion up the hierarchy: the
     * block's XP is awarded once, on its first correct attempt; once every exercise block in a
     * lesson has been answered correctly the lesson is completed; once every lesson in a topic is
     * completed the topic is completed; once every topic in a course version is completed the
     * enrollment is completed.
     */
    @Transactional
    public ExerciseAttempt recordExerciseAttempt(User user, UUID lessonBlockId, boolean isCorrect) {
        LessonBlock block =
                lessonBlockRepository
                        .findById(lessonBlockId)
                        .orElseThrow(() -> new NotFoundException("Lesson block not found"));

        if (block.getType() != BlockType.EXERCISE_ATTEMPT) {
            throw new InvalidInputException("This lesson block does not accept exercise attempts");
        }

        boolean isFirstCorrectAttempt =
                isCorrect
                        && !attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(
                                user.getId(), lessonBlockId);

        ExerciseAttempt attempt =
                attemptRepository.save(
                        ExerciseAttempt.builder()
                                .user(user)
                                .lessonBlock(block)
                                .isCorrect(isCorrect)
                                .build());

        if (isFirstCorrectAttempt) {
            eventPublisher.publishEvent(new XpEarnedEvent(this, user, block.getXpReward()));
            checkLessonCompletion(user, block.getLesson());
        }

        return attempt;
    }

    private void checkLessonCompletion(User user, Lesson lesson) {
        List<LessonBlock> exerciseBlocks =
                lesson.getLessonBlocks().stream()
                        .filter(b -> b.getType() == BlockType.EXERCISE_ATTEMPT)
                        .toList();

        boolean allCompleted =
                !exerciseBlocks.isEmpty()
                        && exerciseBlocks.stream()
                                .allMatch(
                                        b ->
                                                attemptRepository
                                                        .existsByUserIdAndLessonBlockIdAndIsCorrectTrue(
                                                                user.getId(), b.getId()));

        if (!allCompleted) {
            return;
        }

        UserLessonProgress progress =
                lessonProgressRepository
                        .findByUserIdAndLessonId(user.getId(), lesson.getId())
                        .orElseGet(
                                () -> UserLessonProgress.builder().user(user).lesson(lesson).build());

        if (progress.getStatus() == ProgressStatus.COMPLETED) {
            return;
        }

        int xpEarned = exerciseBlocks.stream().mapToInt(LessonBlock::getXpReward).sum();

        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setCompletedAt(Instant.now());
        progress.setXpEarned(xpEarned);
        lessonProgressRepository.save(progress);

        checkTopicCompletion(user, lesson.getTopic());
    }

    private void checkTopicCompletion(User user, Topic topic) {
        List<Lesson> lessons = topic.getLessons();
        List<UUID> completedLessonIds =
                lessonProgressRepository.findByUserIdAndLessonTopicId(user.getId(), topic.getId())
                        .stream()
                        .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                        .map(p -> p.getLesson().getId())
                        .toList();

        boolean allCompleted =
                !lessons.isEmpty()
                        && lessons.stream().allMatch(lesson -> completedLessonIds.contains(lesson.getId()));

        if (!allCompleted) {
            return;
        }

        UserTopicProgress topicProgress =
                topicProgressRepository
                        .findByUserIdAndTopicId(user.getId(), topic.getId())
                        .orElseGet(() -> UserTopicProgress.builder().user(user).topic(topic).build());

        if (topicProgress.getStatus() == ProgressStatus.COMPLETED) {
            return;
        }

        topicProgress.setStatus(ProgressStatus.COMPLETED);
        topicProgress.setCompletedAt(Instant.now());
        topicProgressRepository.save(topicProgress);

        checkCourseCompletion(user, topic.getCourseVersion());
    }

    private void checkCourseCompletion(User user, CourseVersion courseVersion) {
        List<Topic> topics = courseVersion.getTopics();
        List<UUID> completedTopicIds =
                topicProgressRepository
                        .findByUserIdAndTopicCourseVersionId(user.getId(), courseVersion.getId())
                        .stream()
                        .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                        .map(p -> p.getTopic().getId())
                        .toList();

        boolean allCompleted =
                !topics.isEmpty()
                        && topics.stream().allMatch(topic -> completedTopicIds.contains(topic.getId()));

        if (!allCompleted) {
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
