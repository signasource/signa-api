package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves what an organization's progress is measured against: the published version of every
 * course it contracted (or of the one course the panel is filtered by) and how many lessons each
 * one has.
 */
@Component
@RequiredArgsConstructor
public class OrganizationContentScope {

    private static final UUID MATCHES_NOTHING = new UUID(0, 0);

    private final OrganizationCourseRepository organizationCourseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final TopicRepository topicRepository;

    public ContentScope resolve(UUID organizationId) {
        return resolve(organizationId, null);
    }

    /**
     * @param courseId narrows the scope to that course; {@code null} means every contracted course
     * @throws NotFoundException when {@code courseId} is not contracted by the organization
     */
    public ContentScope resolve(UUID organizationId, UUID courseId) {
        List<UUID> courseIds =
                organizationCourseRepository
                        .findByOrganizationIdOrderByContractedAtAsc(organizationId)
                        .stream()
                        .map(OrganizationCourse::getCourse)
                        .map(course -> course.getId())
                        .toList();
        if (courseId != null) {
            if (!courseIds.contains(courseId)) {
                throw new NotFoundException("Course is not contracted by this organization");
            }
            courseIds = List.of(courseId);
        }
        if (courseIds.isEmpty()) {
            return ContentScope.empty();
        }

        List<UUID> contractOrder = courseIds;
        List<CourseVersion> versions =
                courseVersionRepository
                        .findByCourseIdInAndStatus(courseIds, VersionStatus.PUBLISHED)
                        .stream()
                        .sorted(
                                Comparator.comparingInt(
                                        v -> contractOrder.indexOf(v.getCourse().getId())))
                        .toList();
        if (versions.isEmpty()) {
            return ContentScope.empty();
        }

        Map<UUID, Long> lessonsByVersion = new HashMap<>();
        Map<UUID, Long> lessonsByTopic = new HashMap<>();
        long total = 0;
        for (TopicLessonTotalView topic :
                topicRepository.findTopicLessonTotals(
                        versions.stream().map(CourseVersion::getId).toList())) {
            lessonsByVersion.merge(topic.getCourseVersionId(), topic.getTotalLessons(), Long::sum);
            lessonsByTopic.put(topic.getTopicId(), topic.getTotalLessons());
            total += topic.getTotalLessons();
        }
        return new ContentScope(versions, lessonsByVersion, lessonsByTopic, total);
    }

    /** {@code versions} follow the order in which the courses were contracted. */
    public record ContentScope(
            List<CourseVersion> versions,
            Map<UUID, Long> lessonsByVersion,
            Map<UUID, Long> lessonsByTopic,
            long totalLessons) {

        static ContentScope empty() {
            return new ContentScope(List.of(), Map.of(), Map.of(), 0);
        }

        /** Never empty: an empty scope yields an id that matches nothing, so no IN () in SQL. */
        public List<UUID> versionIds() {
            return orNothing(versions.stream().map(CourseVersion::getId).toList());
        }

        /**
         * Attempts are matched by course, so ones made on an older version still count. Never
         * empty, like {@link #versionIds()}.
         */
        public List<UUID> courseIds() {
            return orNothing(versions.stream().map(v -> v.getCourse().getId()).distinct().toList());
        }

        private static List<UUID> orNothing(List<UUID> ids) {
            return ids.isEmpty() ? List.of(MATCHES_NOTHING) : ids;
        }

        public boolean isEmpty() {
            return versions.isEmpty();
        }

        /** Course by course in contract order, then each course's own module order. */
        public Comparator<Topic> topicOrder() {
            List<UUID> order = versionIds();
            return Comparator.comparingInt((Topic t) -> order.indexOf(t.getCourseVersion().getId()))
                    .thenComparingInt(Topic::getOrder);
        }
    }
}
