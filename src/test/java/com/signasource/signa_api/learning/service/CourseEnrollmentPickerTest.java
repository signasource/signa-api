package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.ToDoubleFunction;
import org.junit.jupiter.api.Test;

class CourseEnrollmentPickerTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:00:00Z");
    private static final ToDoubleFunction<UserCourseEnrollment> NO_PROGRESS = e -> 0;

    private final Course cafe = Course.builder().id(UUID.randomUUID()).build();
    private final CourseVersion v1 =
            CourseVersion.builder().id(UUID.randomUUID()).course(cafe).build();
    private final CourseVersion v2 =
            CourseVersion.builder().id(UUID.randomUUID()).course(cafe).build();

    private static UserCourseEnrollment enrollment(
            CourseVersion version, EnrollmentStatus status, Instant startedAt, Instant expiresAt) {
        return UserCourseEnrollment.builder()
                .courseVersion(version)
                .status(status)
                .startedAt(startedAt)
                .accessExpiresAt(expiresAt)
                .build();
    }

    private static ToDoubleFunction<UserCourseEnrollment> progress(
            Map<UserCourseEnrollment, Double> byEnrollment) {
        return e -> byEnrollment.getOrDefault(e, 0.0);
    }

    @Test
    void shouldKeepTheFinishedVersionOverAnEmptyNewerOne() {
        UserCourseEnrollment finished =
                enrollment(v1, EnrollmentStatus.COMPLETED, NOW.minus(5, ChronoUnit.DAYS), null);
        UserCourseEnrollment empty =
                enrollment(v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS), null);

        assertSame(
                finished,
                CourseEnrollmentPicker.pick(
                                List.of(empty, finished), NOW, progress(Map.of(finished, 1.0)))
                        .orElseThrow());
    }

    @Test
    void shouldPreferTheMostRecentOnATie() {
        UserCourseEnrollment older =
                enrollment(v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS), null);
        UserCourseEnrollment newer =
                enrollment(v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS), null);

        assertSame(
                newer,
                CourseEnrollmentPicker.pick(List.of(newer, older), NOW, NO_PROGRESS).orElseThrow());
    }

    @Test
    void shouldPreferAnEnrollmentThatStillGrantsAccess() {
        UserCourseEnrollment expired =
                enrollment(
                        v1,
                        EnrollmentStatus.COMPLETED,
                        NOW.minus(5, ChronoUnit.DAYS),
                        NOW.minus(1, ChronoUnit.DAYS));
        UserCourseEnrollment active =
                enrollment(v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS), null);

        assertSame(
                active,
                CourseEnrollmentPicker.pick(
                                List.of(expired, active), NOW, progress(Map.of(expired, 1.0)))
                        .orElseThrow());
    }

    @Test
    void shouldFallBackToExpiredThenToDroppedEnrollments() {
        UserCourseEnrollment expired =
                enrollment(
                        v1,
                        EnrollmentStatus.ENROLLED,
                        NOW.minus(5, ChronoUnit.DAYS),
                        NOW.minus(1, ChronoUnit.DAYS));
        UserCourseEnrollment dropped =
                enrollment(v2, EnrollmentStatus.DROPPED, NOW.minus(1, ChronoUnit.HOURS), null);

        assertSame(
                expired,
                CourseEnrollmentPicker.pick(
                                List.of(expired, dropped), NOW, progress(Map.of(dropped, 1.0)))
                        .orElseThrow());
        assertSame(
                dropped,
                CourseEnrollmentPicker.pick(List.of(dropped), NOW, NO_PROGRESS).orElseThrow());
    }

    @Test
    void shouldReturnNothingWithoutCandidates() {
        assertTrue(CourseEnrollmentPicker.pick(List.of(), NOW, NO_PROGRESS).isEmpty());
    }

    @Test
    void shouldKeepOneEnrollmentPerCourse() {
        Course basics = Course.builder().id(UUID.randomUUID()).build();
        CourseVersion basicsV1 =
                CourseVersion.builder().id(UUID.randomUUID()).course(basics).build();
        UserCourseEnrollment cafeOld =
                enrollment(v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS), null);
        UserCourseEnrollment basicsOnly =
                enrollment(
                        basicsV1, EnrollmentStatus.ENROLLED, NOW.minus(3, ChronoUnit.DAYS), null);
        UserCourseEnrollment cafeNew =
                enrollment(v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS), null);

        Map<UUID, UserCourseEnrollment> result =
                CourseEnrollmentPicker.perCourse(
                        List.of(cafeOld, basicsOnly, cafeNew), NOW, NO_PROGRESS);

        assertEquals(List.of(cafe.getId(), basics.getId()), List.copyOf(result.keySet()));
        assertSame(cafeNew, result.get(cafe.getId()));
        assertSame(basicsOnly, result.get(basics.getId()));
    }
}
