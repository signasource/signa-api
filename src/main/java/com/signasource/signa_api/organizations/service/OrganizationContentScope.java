package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves what an organization's progress is measured against: the versions members are enrolled
 * in for each contracted course, falling back to the current published version for courses with no
 * enrollments yet. Includes how many lessons each version has.
 */
@Component
@RequiredArgsConstructor
public class OrganizationContentScope {

    private static final UUID MATCHES_NOTHING = new UUID(0, 0);

    private final OrganizationCourseRepository organizationCourseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
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
        List<CourseVersion> versions = resolveVersions(organizationId, courseIds, contractOrder);
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

    /**
     * Prefers the versions org members are actively enrolled in over the current published version.
     * When no enrollments exist for a course (not yet started), the published version is used as a
     * fallback so the scope is never empty for a course the org contracted.
     */
    private List<CourseVersion> resolveVersions(
            UUID organizationId, List<UUID> courseIds, List<UUID> contractOrder) {
        List<UUID> enrolledIds =
                enrollmentRepository.findDistinctVersionIdsByOrganizationAndCourseIds(
                        organizationId, courseIds, EnrollmentStatus.DROPPED);

        if (enrolledIds.isEmpty()) {
            return courseVersionRepository
                    .findByCourseIdInAndStatus(courseIds, VersionStatus.PUBLISHED)
                    .stream()
                    .sorted(
                            Comparator.comparingInt(
                                    v -> contractOrder.indexOf(v.getCourse().getId())))
                    .toList();
        }

        List<CourseVersion> enrolledVersions =
                courseVersionRepository.findWithCourseByIds(enrolledIds);
        Set<UUID> coveredCourseIds =
                enrolledVersions.stream()
                        .map(v -> v.getCourse().getId())
                        .collect(Collectors.toSet());

        List<UUID> unenrolledCourseIds =
                courseIds.stream().filter(id -> !coveredCourseIds.contains(id)).toList();

        List<CourseVersion> all = new ArrayList<>(enrolledVersions);
        if (!unenrolledCourseIds.isEmpty()) {
            all.addAll(
                    courseVersionRepository.findByCourseIdInAndStatus(
                            unenrolledCourseIds, VersionStatus.PUBLISHED));
        }
        return all.stream()
                .sorted(Comparator.comparingInt(v -> contractOrder.indexOf(v.getCourse().getId())))
                .toList();
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
