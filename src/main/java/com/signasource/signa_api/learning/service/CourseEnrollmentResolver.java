package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.learning.repository.projection.TopicCompletedCountView;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Applies {@link CourseEnrollmentPicker} to one learner, loading how far they got in each version
 * only when a course actually has more than one enrollment to choose from.
 */
@Component
@RequiredArgsConstructor
public class CourseEnrollmentResolver {

    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final UserLessonProgressRepository lessonProgressRepository;
    private final TopicRepository topicRepository;

    /** The learner's enrollment in that course, in whatever version they take it. */
    public Optional<UserCourseEnrollment> forCourse(UUID userId, UUID courseId, Instant at) {
        List<UserCourseEnrollment> candidates =
                enrollmentRepository.findByUserIdAndCourseVersionCourseId(userId, courseId);
        return CourseEnrollmentPicker.pick(candidates, at, completion(userId, candidates));
    }

    /** One enrollment per course out of {@code enrollments} (all of the same learner). */
    public Map<UUID, UserCourseEnrollment> perCourse(
            UUID userId, Collection<UserCourseEnrollment> enrollments, Instant at) {
        return CourseEnrollmentPicker.perCourse(enrollments, at, completion(userId, enrollments));
    }

    private ToDoubleFunction<UserCourseEnrollment> completion(
            UUID userId, Collection<UserCourseEnrollment> enrollments) {
        long courses =
                enrollments.stream().map(CourseEnrollmentPicker::courseId).distinct().count();
        if (courses == enrollments.size()) {
            return e -> 0; // nothing to choose between
        }
        List<UUID> versionIds =
                enrollments.stream().map(e -> e.getCourseVersion().getId()).distinct().toList();
        Map<UUID, UUID> versionOfTopic = new HashMap<>();
        Map<UUID, Long> lessonsByVersion = new HashMap<>();
        for (TopicLessonTotalView topic : topicRepository.findTopicLessonTotals(versionIds)) {
            versionOfTopic.put(topic.getTopicId(), topic.getCourseVersionId());
            lessonsByVersion.merge(topic.getCourseVersionId(), topic.getTotalLessons(), Long::sum);
        }
        Map<UUID, Long> doneByVersion = new HashMap<>();
        for (TopicCompletedCountView topic :
                lessonProgressRepository.findCompletedLessonCountsByTopic(userId, versionIds)) {
            UUID version = versionOfTopic.get(topic.getTopicId());
            if (version != null) {
                doneByVersion.merge(version, topic.getCompletedLessons(), Long::sum);
            }
        }
        return e -> {
            UUID version = e.getCourseVersion().getId();
            long total = lessonsByVersion.getOrDefault(version, 0L);
            return total == 0
                    ? 0
                    : Math.min(1.0, doneByVersion.getOrDefault(version, 0L) / (double) total);
        };
    }
}
