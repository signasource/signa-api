package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.Topic;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.projection.TopicLessonTotalView;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationMetricsRepository;
import com.signasource.signa_api.organizations.repository.projection.UserVersionCountView;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Resolves what each participant's progress is measured against.
 *
 * <p>A course gets new versions over time (publishing one archives the previous), and lesson and
 * module progress belong to a specific version. So every participant is measured, course by course,
 * against exactly <b>one assigned version</b> (see {@link #assign}): the version of their own
 * enrollment in that course, never the sum of several versions and never a version they are not
 * taking. Someone with no enrollment yet is measured against the course's <b>reference version</b>
 * (the published one), so they count as 0 % of the current content.
 */
@Component
@RequiredArgsConstructor
public class OrganizationContentScope {

    private static final UUID MATCHES_NOTHING = new UUID(0, 0);

    private final OrganizationCourseRepository organizationCourseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final OrganizationMetricsRepository metricsRepository;
    private final TopicRepository topicRepository;

    public ContentScope resolve(UUID organizationId, Collection<UUID> userIds, Instant until) {
        return resolve(organizationId, null, userIds, until);
    }

    /**
     * @param courseId narrows the scope to that course; {@code null} means every contracted course
     * @param userIds the participants to assign versions to
     * @param until progress and enrollments are taken as of this instant
     * @throws NotFoundException when {@code courseId} is not contracted by the organization
     */
    public ContentScope resolve(
            UUID organizationId, UUID courseId, Collection<UUID> userIds, Instant until) {
        List<Course> courses = contractedCourses(organizationId, courseId);
        if (courses.isEmpty()) {
            return ContentScope.empty();
        }
        List<UUID> courseIds = courses.stream().map(Course::getId).toList();

        Map<UUID, CourseVersion> published = new HashMap<>();
        for (CourseVersion version :
                courseVersionRepository.findByCourseIdInAndStatus(
                        courseIds, VersionStatus.PUBLISHED)) {
            published.merge(version.getCourse().getId(), version, OrganizationContentScope::newer);
        }

        List<UserCourseEnrollment> enrollments =
                userIds.isEmpty()
                        ? List.of()
                        : enrollmentRepository
                                .findWithCourseByUserIdInAndCourseIdIn(userIds, courseIds)
                                .stream()
                                .filter(
                                        e ->
                                                e.getStartedAt() == null
                                                        || e.getStartedAt().isBefore(until))
                                .toList();

        Map<UUID, CourseVersion> versions = new LinkedHashMap<>();
        published.values().forEach(v -> versions.put(v.getId(), v));
        enrollments.forEach(
                e -> versions.putIfAbsent(e.getCourseVersion().getId(), e.getCourseVersion()));

        List<CourseScope> scoped = new ArrayList<>();
        for (Course course : courses) {
            referenceVersion(course.getId(), published, enrollments)
                    .ifPresent(reference -> scoped.add(new CourseScope(course, reference)));
        }
        if (scoped.isEmpty()) {
            return ContentScope.empty();
        }

        Map<UUID, Long> lessonsByVersion = new HashMap<>();
        Map<UUID, Long> lessonsByTopic = new HashMap<>();
        for (TopicLessonTotalView topic :
                topicRepository.findTopicLessonTotals(List.copyOf(versions.keySet()))) {
            lessonsByVersion.merge(topic.getCourseVersionId(), topic.getTotalLessons(), Long::sum);
            lessonsByTopic.put(topic.getTopicId(), topic.getTotalLessons());
        }

        Map<UUID, Map<UUID, Long>> completed = new HashMap<>();
        if (!userIds.isEmpty()) {
            for (UserVersionCountView row :
                    metricsRepository.findCompletedLessonCountsByUserAndVersion(
                            userIds, List.copyOf(versions.keySet()), until)) {
                completed
                        .computeIfAbsent(row.getUserId(), u -> new HashMap<>())
                        .put(row.getVersionId(), row.getTotal());
            }
        }

        Map<UUID, Map<UUID, List<UserCourseEnrollment>>> byUserAndCourse =
                enrollments.stream()
                        .collect(
                                Collectors.groupingBy(
                                        e -> e.getUser().getId(),
                                        Collectors.groupingBy(
                                                e -> e.getCourseVersion().getCourse().getId())));
        Map<UUID, Map<UUID, UUID>> assignments = new HashMap<>();
        byUserAndCourse.forEach(
                (userId, perCourse) -> {
                    Map<UUID, Long> done = completed.getOrDefault(userId, Map.of());
                    Map<UUID, UUID> assigned = new HashMap<>();
                    perCourse.forEach(
                            (course, candidates) ->
                                    assigned.put(
                                            course, assign(candidates, done, lessonsByVersion)));
                    assignments.put(userId, assigned);
                });

        return new ContentScope(
                List.copyOf(scoped),
                versions,
                lessonsByVersion,
                lessonsByTopic,
                assignments,
                completed);
    }

    /**
     * The version a participant is measured against, among their enrollments in one course.
     *
     * <ol>
     *   <li>Enrollments still in force come first; dropped ones only count when there is nothing
     *       else (a removed participant keeps showing the progress they made).
     *   <li>The enrollment where they got furthest wins: finishing a course is never undone by a
     *       newer version being published, nor by an empty enrollment in it.
     *   <li>On a tie, the most recent enrollment: the one they are taking now.
     * </ol>
     */
    static UUID assign(
            List<UserCourseEnrollment> candidates,
            Map<UUID, Long> completedByVersion,
            Map<UUID, Long> lessonsByVersion) {
        List<UserCourseEnrollment> inForce =
                candidates.stream().filter(e -> e.getStatus() != EnrollmentStatus.DROPPED).toList();
        List<UserCourseEnrollment> pool = inForce.isEmpty() ? candidates : inForce;
        Comparator<UserCourseEnrollment> furthest =
                Comparator.comparingDouble(
                        e ->
                                completion(
                                        e.getCourseVersion().getId(),
                                        completedByVersion,
                                        lessonsByVersion));
        return pool.stream()
                .max(
                        furthest.thenComparing(
                                UserCourseEnrollment::getStartedAt,
                                Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElseThrow()
                .getCourseVersion()
                .getId();
    }

    private static double completion(
            UUID versionId, Map<UUID, Long> completedByVersion, Map<UUID, Long> lessonsByVersion) {
        long total = lessonsByVersion.getOrDefault(versionId, 0L);
        if (total == 0) {
            return 0;
        }
        return Math.min(1.0, completedByVersion.getOrDefault(versionId, 0L) / (double) total);
    }

    /** The published version or, for a course with none, the newest one someone is enrolled in. */
    private static Optional<CourseVersion> referenceVersion(
            UUID courseId,
            Map<UUID, CourseVersion> published,
            List<UserCourseEnrollment> enrollments) {
        CourseVersion current = published.get(courseId);
        if (current != null) {
            return Optional.of(current);
        }
        return enrollments.stream()
                .map(UserCourseEnrollment::getCourseVersion)
                .filter(v -> v.getCourse().getId().equals(courseId))
                .reduce(OrganizationContentScope::newer);
    }

    private static CourseVersion newer(CourseVersion a, CourseVersion b) {
        Comparator<CourseVersion> byPublication =
                Comparator.comparing(
                        CourseVersion::getPublishedAt,
                        Comparator.nullsFirst(Comparator.naturalOrder()));
        return byPublication.compare(a, b) >= 0 ? a : b;
    }

    private List<Course> contractedCourses(UUID organizationId, UUID courseId) {
        List<Course> courses =
                organizationCourseRepository
                        .findByOrganizationIdOrderByContractedAtAsc(organizationId)
                        .stream()
                        .map(OrganizationCourse::getCourse)
                        .toList();
        if (courseId == null) {
            return courses;
        }
        return List.of(
                courses.stream()
                        .filter(c -> c.getId().equals(courseId))
                        .findFirst()
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Course is not contracted by this organization")));
    }

    /** A contracted course and the version shown for it when there is no participant to follow. */
    public record CourseScope(Course course, CourseVersion reference) {}

    /**
     * The contracted courses (in contract order), every version involved (the references plus the
     * ones participants are enrolled in), and which version each participant is measured against.
     */
    public record ContentScope(
            List<CourseScope> courses,
            Map<UUID, CourseVersion> versions,
            Map<UUID, Long> lessonsByVersion,
            Map<UUID, Long> lessonsByTopic,
            Map<UUID, Map<UUID, UUID>> assignments,
            Map<UUID, Map<UUID, Long>> completedLessons) {

        static ContentScope empty() {
            return new ContentScope(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
        }

        public boolean isEmpty() {
            return courses.isEmpty();
        }

        /**
         * Attempts are matched by course, so ones made on any version count. Never empty: an empty
         * scope yields an id that matches nothing, so no IN () in SQL.
         */
        public List<UUID> courseIds() {
            return orNothing(courses.stream().map(c -> c.course().getId()).toList());
        }

        /**
         * Every version in scope, for things that <i>happened</i> (lessons or modules completed in
         * a period). Never empty, like {@link #courseIds()}.
         */
        public List<UUID> versionIds() {
            return orNothing(List.copyOf(versions.keySet()));
        }

        /** The version {@code userId} is measured against in each course, in contract order. */
        public List<CourseVersion> versionsOf(UUID userId) {
            Map<UUID, UUID> assigned = assignments.getOrDefault(userId, Map.of());
            return courses.stream()
                    .map(
                            c ->
                                    versions.get(
                                            assigned.getOrDefault(
                                                    c.course().getId(), c.reference().getId())))
                    .toList();
        }

        /** Like {@link #versionsOf}, as ids; never empty. */
        public List<UUID> versionIdsOf(UUID userId) {
            return orNothing(versionsOf(userId).stream().map(CourseVersion::getId).toList());
        }

        public boolean isAssigned(UUID userId, UUID versionId) {
            return versionsOf(userId).stream().anyMatch(v -> v.getId().equals(versionId));
        }

        public long lessonsIn(UUID versionId) {
            return lessonsByVersion.getOrDefault(versionId, 0L);
        }

        /** Lessons {@code userId} completed in that version, capped at the version's size. */
        public long completedIn(UUID userId, UUID versionId) {
            long done = completedLessons.getOrDefault(userId, Map.of()).getOrDefault(versionId, 0L);
            return Math.min(done, lessonsIn(versionId));
        }

        /** Lessons of every course {@code userId} is measured against. */
        public long totalLessons(UUID userId) {
            return versionsOf(userId).stream().mapToLong(v -> lessonsIn(v.getId())).sum();
        }

        /** Lessons {@code userId} completed, counting only their assigned versions. */
        public long completedLessons(UUID userId) {
            return versionsOf(userId).stream().mapToLong(v -> completedIn(userId, v.getId())).sum();
        }

        /** The course a version belongs to, or {@code null} when it is not in scope. */
        public Course courseOf(UUID versionId) {
            CourseVersion version = versions.get(versionId);
            return version == null ? null : version.getCourse();
        }

        /** Course by course in contract order, then each course's own module order. */
        public Comparator<Topic> topicOrder() {
            List<UUID> order = courses.stream().map(c -> c.course().getId()).toList();
            return Comparator.comparingInt(
                            (Topic t) -> {
                                Course course = courseOf(t.getCourseVersion().getId());
                                return course == null
                                        ? Integer.MAX_VALUE
                                        : order.indexOf(course.getId());
                            })
                    .thenComparingInt(Topic::getOrder);
        }

        private static List<UUID> orNothing(List<UUID> ids) {
            return ids.isEmpty() ? List.of(MATCHES_NOTHING) : ids;
        }
    }
}
