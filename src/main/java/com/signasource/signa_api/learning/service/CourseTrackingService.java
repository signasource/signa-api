package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.ResourceAlreadyInUseException;
import com.signasource.signa_api.learning.dto.CourseProgressResponse;
import com.signasource.signa_api.learning.dto.TopicProgressResponse;
import com.signasource.signa_api.learning.entity.BlockType;
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
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
    private final LessonBlockAttemptRepository attemptRepository;

    private final CourseVersionRepository courseVersionRepository;
    private final LessonBlockRepository lessonBlockRepository;
    private final TopicRepository topicRepository;

    private final ApplicationEventPublisher eventPublisher;

    private static final int SIGNS_LEARNED = 0;

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

    @Transactional(readOnly = true)
    public List<CourseProgressResponse> getUserCourseProgress(User user) {
        List<UserCourseEnrollment> enrollments =
                enrollmentRepository.findWithCourseByUserId(user.getId());
        if (enrollments.isEmpty()) {
            return List.of();
        }

        List<UUID> versionIds =
                enrollments.stream().map(e -> e.getCourseVersion().getId()).toList();

        Map<UUID, Long> completedByTopic =
                lessonProgressRepository
                        .findCompletedLessonCountsByTopic(user.getId(), versionIds)
                        .stream()
                        .collect(
                                Collectors.toMap(
                                        TopicCompletedCountView::getTopicId,
                                        TopicCompletedCountView::getCompletedLessons));

        Map<UUID, long[]> lessonCountsByVersion = new HashMap<>();
        Map<UUID, Long> totalByTopic = new HashMap<>();
        for (TopicLessonTotalView topic : topicRepository.findTopicLessonTotals(versionIds)) {
            long completed = completedByTopic.getOrDefault(topic.getTopicId(), 0L);
            long[] counts =
                    lessonCountsByVersion.computeIfAbsent(
                            topic.getCourseVersionId(), k -> new long[2]);
            counts[0] += topic.getTotalLessons();
            counts[1] += completed;
            totalByTopic.put(topic.getTopicId(), topic.getTotalLessons());
        }

        Map<UUID, Topic> inProgressTopicByVersion = new HashMap<>();
        for (UserTopicProgress progress :
                topicProgressRepository.findInProgressTopics(user.getId(), versionIds)) {
            Topic topic = progress.getTopic();
            inProgressTopicByVersion.putIfAbsent(topic.getCourseVersion().getId(), topic);
        }

        List<CourseProgressResponse> result = new ArrayList<>(enrollments.size());
        for (UserCourseEnrollment enrollment : enrollments) {
            UUID versionId = enrollment.getCourseVersion().getId();
            long[] counts = lessonCountsByVersion.getOrDefault(versionId, new long[2]);
            TopicProgressResponse currentTopic =
                    currentTopic(
                            inProgressTopicByVersion.get(versionId),
                            totalByTopic,
                            completedByTopic);
            result.add(
                    new CourseProgressResponse(
                            enrollment.getCourseVersion().getCourse().getName(),
                            enrollment.getStatus(),
                            (int) counts[0],
                            (int) counts[1],
                            percentage(counts[1], counts[0]),
                            SIGNS_LEARNED,
                            currentTopic));
        }
        return result;
    }

    private static TopicProgressResponse currentTopic(
            Topic topic, Map<UUID, Long> totalByTopic, Map<UUID, Long> completedByTopic) {
        if (topic == null) {
            return null;
        }
        long total = totalByTopic.getOrDefault(topic.getId(), 0L);
        long completed = completedByTopic.getOrDefault(topic.getId(), 0L);
        return new TopicProgressResponse(
                topic.getName(), (int) total, (int) completed, percentage(completed, total));
    }

    private static int percentage(long completed, long total) {
        return total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);
    }

    /**
     * Records a user's interaction with a lesson block — an exercise attempt (isCorrect set) or an
     * INFO block view (isCorrect null) — and cascades completion up the hierarchy: the block's XP
     * is awarded once, on its first correct attempt/view; once every block in a lesson has been
     * resolved by the user the lesson is completed; once every lesson in a topic is completed the
     * topic is completed; once every topic in a course version is completed the enrollment is
     * completed.
     */
    @Transactional
    public LessonBlockAttempt recordBlockInteraction(
            User user, UUID lessonBlockId, Boolean isCorrect) {
        LessonBlock block =
                lessonBlockRepository
                        .findById(lessonBlockId)
                        .orElseThrow(() -> new NotFoundException("Lesson block not found"));

        boolean isInfo = block.getType() == BlockType.INFO;
        if (isInfo && isCorrect != null) {
            throw new InvalidInputException("INFO blocks do not accept a correctness value");
        }
        if (!isInfo && isCorrect == null) {
            throw new InvalidInputException("This lesson block requires a correctness value");
        }

        boolean isNewMilestone =
                isInfo
                        ? !attemptRepository.existsByUserIdAndLessonBlockId(
                                user.getId(), lessonBlockId)
                        : isCorrect
                                && !attemptRepository
                                        .existsByUserIdAndLessonBlockIdAndIsCorrectTrue(
                                                user.getId(), lessonBlockId);

        LessonBlockAttempt attempt =
                attemptRepository.save(
                        LessonBlockAttempt.builder()
                                .user(user)
                                .lessonBlock(block)
                                .isCorrect(isCorrect)
                                .build());

        markInProgress(user, block.getLesson());

        if (isNewMilestone) {
            if (!isInfo || block.getXpReward() != null) {
                eventPublisher.publishEvent(new XpEarnedEvent(this, user, block.getXpReward()));
            }
            checkLessonCompletion(user, block.getLesson());
        }

        return attempt;
    }

    /**
     * Marks the lesson (and, transitively, its topic) as IN_PROGRESS on the user's first
     * interaction with it. Never downgrades a COMPLETED lesson/topic back to IN_PROGRESS.
     */
    private void markInProgress(User user, Lesson lesson) {
        UserLessonProgress lessonProgress =
                lessonProgressRepository
                        .findByUserIdAndLessonId(user.getId(), lesson.getId())
                        .orElseGet(
                                () ->
                                        UserLessonProgress.builder()
                                                .user(user)
                                                .lesson(lesson)
                                                .status(ProgressStatus.LOCKED)
                                                .build());

        if (lessonProgress.getStatus() != ProgressStatus.LOCKED) {
            return;
        }

        lessonProgress.setStatus(ProgressStatus.IN_PROGRESS);
        lessonProgress.setStartedAt(Instant.now());
        lessonProgressRepository.save(lessonProgress);

        Topic topic = lesson.getTopic();
        UserTopicProgress topicProgress =
                topicProgressRepository
                        .findByUserIdAndTopicId(user.getId(), topic.getId())
                        .orElseGet(
                                () ->
                                        UserTopicProgress.builder()
                                                .user(user)
                                                .topic(topic)
                                                .status(ProgressStatus.LOCKED)
                                                .build());

        if (topicProgress.getStatus() != ProgressStatus.LOCKED) {
            return;
        }

        topicProgress.setStatus(ProgressStatus.IN_PROGRESS);
        topicProgress.setStartedAt(Instant.now());
        topicProgressRepository.save(topicProgress);
    }

    private boolean isCompletedByUser(User user, LessonBlock block) {
        if (block.getType() == BlockType.INFO) {
            return attemptRepository.existsByUserIdAndLessonBlockId(user.getId(), block.getId());
        }
        return attemptRepository.existsByUserIdAndLessonBlockIdAndIsCorrectTrue(
                user.getId(), block.getId());
    }

    private void checkLessonCompletion(User user, Lesson lesson) {
        List<LessonBlock> blocks = lesson.getLessonBlocks();

        boolean allCompleted =
                !blocks.isEmpty() && blocks.stream().allMatch(b -> isCompletedByUser(user, b));

        if (!allCompleted) {
            return;
        }

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

        int xpEarned =
                blocks.stream().mapToInt(b -> b.getXpReward() == null ? 0 : b.getXpReward()).sum();

        progress.setStatus(ProgressStatus.COMPLETED);
        progress.setCompletedAt(Instant.now());
        progress.setXpEarned(xpEarned);
        lessonProgressRepository.save(progress);

        checkTopicCompletion(user, lesson.getTopic());
    }

    private void checkTopicCompletion(User user, Topic topic) {
        List<Lesson> lessons = topic.getLessons();
        List<UUID> completedLessonIds =
                lessonProgressRepository
                        .findByUserIdAndLessonTopicId(user.getId(), topic.getId())
                        .stream()
                        .filter(p -> p.getStatus() == ProgressStatus.COMPLETED)
                        .map(p -> p.getLesson().getId())
                        .toList();

        boolean allCompleted =
                !lessons.isEmpty()
                        && lessons.stream()
                                .allMatch(lesson -> completedLessonIds.contains(lesson.getId()));

        if (!allCompleted) {
            return;
        }

        UserTopicProgress topicProgress =
                topicProgressRepository
                        .findByUserIdAndTopicId(user.getId(), topic.getId())
                        .orElseGet(
                                () -> UserTopicProgress.builder().user(user).topic(topic).build());

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
                        && topics.stream()
                                .allMatch(topic -> completedTopicIds.contains(topic.getId()));

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
