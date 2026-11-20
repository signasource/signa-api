package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * The one rule for "which enrollment is this learner's course".
 *
 * <p>A learner takes a course in one version: publishing a new version archives the old one but
 * leaves whoever was taking it there, so their progress is never interrupted. Granting access again
 * reuses that enrollment instead of opening another one (see {@link CourseEnrollmentResolver}).
 * Older data may still hold several enrollments of one course in different versions; every reader
 * (the app, the organization panel) resolves them with this same rule so they always agree:
 *
 * <ol>
 *   <li>enrollments that grant access at {@code at} first; then those not dropped; then any;
 *   <li>among them, the one where the learner got furthest, so finishing a course is never undone
 *       by an empty enrollment in a newer version;
 *   <li>on a tie, the most recent one: the one they are taking now.
 * </ol>
 */
public final class CourseEnrollmentPicker {

    private CourseEnrollmentPicker() {}

    /**
     * @param completion share of the enrollment's version the learner completed, 0 to 1
     */
    public static Optional<UserCourseEnrollment> pick(
            Collection<UserCourseEnrollment> candidates,
            Instant at,
            ToDoubleFunction<UserCourseEnrollment> completion) {
        List<UserCourseEnrollment> withAccess =
                candidates.stream().filter(e -> e.grantsAccess(at)).toList();
        List<UserCourseEnrollment> notDropped =
                candidates.stream().filter(e -> e.getStatus() != EnrollmentStatus.DROPPED).toList();
        Collection<UserCourseEnrollment> pool =
                !withAccess.isEmpty()
                        ? withAccess
                        : !notDropped.isEmpty() ? notDropped : candidates;
        return pool.stream()
                .max(
                        Comparator.comparingDouble(completion)
                                .thenComparing(
                                        UserCourseEnrollment::getStartedAt,
                                        Comparator.nullsFirst(Comparator.naturalOrder())));
    }

    /** One enrollment per course, keyed by course id, in the order the courses first appear. */
    public static Map<UUID, UserCourseEnrollment> perCourse(
            Collection<UserCourseEnrollment> enrollments,
            Instant at,
            ToDoubleFunction<UserCourseEnrollment> completion) {
        Map<UUID, List<UserCourseEnrollment>> byCourse =
                enrollments.stream()
                        .collect(
                                Collectors.groupingBy(
                                        CourseEnrollmentPicker::courseId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        Map<UUID, UserCourseEnrollment> result = new LinkedHashMap<>();
        byCourse.forEach(
                (courseId, candidates) ->
                        pick(candidates, at, completion)
                                .ifPresent(chosen -> result.put(courseId, chosen)));
        return result;
    }

    public static UUID courseId(UserCourseEnrollment enrollment) {
        return enrollment.getCourseVersion().getCourse().getId();
    }
}
