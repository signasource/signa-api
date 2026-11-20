package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
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
import com.signasource.signa_api.organizations.service.OrganizationContentScope.ContentScope;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationContentScopeTest {

    private static final Instant NOW = Instant.parse("2026-11-20T20:00:00Z");

    @Mock private OrganizationCourseRepository organizationCourseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private OrganizationMetricsRepository metricsRepository;
    @Mock private TopicRepository topicRepository;

    @InjectMocks private OrganizationContentScope contentScope;

    private final UUID organizationId = UUID.randomUUID();
    private Course cafe;
    private CourseVersion v1;
    private CourseVersion v2;
    private User mateo;
    private final List<CourseVersion> published = new ArrayList<>();
    private final List<TopicLessonTotalView> lessonTotals = new ArrayList<>();
    private final List<UserVersionCountView> completed = new ArrayList<>();
    private final List<UserCourseEnrollment> enrollments = new ArrayList<>();

    @BeforeEach
    void setUp() {
        cafe = Course.builder().id(UUID.randomUUID()).name("En la cafetería").build();
        v1 = version(cafe, VersionStatus.ARCHIVED, NOW.minus(10, ChronoUnit.DAYS));
        v2 = version(cafe, VersionStatus.PUBLISHED, NOW.minus(1, ChronoUnit.DAYS));
        published.add(v2);
        mateo = user();
        lessonTotals.add(total(v1.getId(), UUID.randomUUID(), 3));
        lessonTotals.add(total(v2.getId(), UUID.randomUUID(), 3));
    }

    private static CourseVersion version(Course course, VersionStatus status, Instant publishedAt) {
        return CourseVersion.builder()
                .id(UUID.randomUUID())
                .course(course)
                .status(status)
                .publishedAt(publishedAt)
                .build();
    }

    private static User user() {
        User user = new User();
        user.setId(UUID.randomUUID());
        return user;
    }

    private static TopicLessonTotalView total(UUID versionId, UUID topicId, long lessons) {
        TopicLessonTotalView view = mock(TopicLessonTotalView.class);
        lenient().when(view.getCourseVersionId()).thenReturn(versionId);
        lenient().when(view.getTopicId()).thenReturn(topicId);
        lenient().when(view.getTotalLessons()).thenReturn(lessons);
        return view;
    }

    private void enroll(User user, CourseVersion version, EnrollmentStatus status, Instant at) {
        enrollments.add(
                UserCourseEnrollment.builder()
                        .user(user)
                        .courseVersion(version)
                        .status(status)
                        .startedAt(at)
                        .build());
    }

    private void done(User user, CourseVersion version, long lessons) {
        UserVersionCountView view = mock(UserVersionCountView.class);
        lenient().when(view.getUserId()).thenReturn(user.getId());
        lenient().when(view.getVersionId()).thenReturn(version.getId());
        lenient().when(view.getTotal()).thenReturn(lessons);
        completed.add(view);
    }

    private void contract(Course... courses) {
        List<OrganizationCourse> contracted = new ArrayList<>();
        for (Course course : courses) {
            contracted.add(OrganizationCourse.builder().course(course).build());
        }
        when(organizationCourseRepository.findByOrganizationIdOrderByContractedAtAsc(
                        organizationId))
                .thenReturn(contracted);
    }

    private ContentScope resolve(UUID courseId, User... users) {
        lenient()
                .when(
                        courseVersionRepository.findByCourseIdInAndStatus(
                                any(), eq(VersionStatus.PUBLISHED)))
                .thenReturn(published);
        lenient()
                .when(enrollmentRepository.findWithCourseByUserIdInAndCourseIdIn(any(), any()))
                .thenReturn(enrollments);
        lenient().when(topicRepository.findTopicLessonTotals(any())).thenReturn(lessonTotals);
        lenient()
                .when(
                        metricsRepository.findCompletedLessonCountsByUserAndVersion(
                                anyCollection(), anyCollection(), eq(NOW)))
                .thenReturn(completed);
        return contentScope.resolve(
                organizationId, courseId, List.of(users).stream().map(User::getId).toList(), NOW);
    }

    private ContentScope resolve(User... users) {
        return resolve(null, users);
    }

    @Test
    void shouldBeEmptyWhenNoCourseIsContracted() {
        contract();

        ContentScope scope = contentScope.resolve(organizationId, List.of(mateo.getId()), NOW);

        assertTrue(scope.isEmpty());
        assertEquals(0, scope.totalLessons(mateo.getId()));
        assertEquals(1, scope.courseIds().size());
        assertEquals(1, scope.versionIds().size());
        assertEquals(1, scope.versionIdsOf(mateo.getId()).size());
        verifyNoInteractions(courseVersionRepository, topicRepository, metricsRepository);
    }

    @Test
    void shouldBeEmptyWhenACourseHasNoPublishedVersionAndNobodyIsEnrolled() {
        contract(cafe);
        published.clear();

        assertTrue(resolve().isEmpty());
        verifyNoInteractions(topicRepository);
    }

    /** The reported bug: finished v1, then got an empty enrollment in the newly published v2. */
    @Test
    void shouldKeepAFinishedCourseAtOneHundredPercentWhenANewVersionIsPublished() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));
        enroll(mateo, v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS));
        done(mateo, v1, 3);

        ContentScope scope = resolve(mateo);

        assertEquals(List.of(v1), scope.versionsOf(mateo.getId()));
        assertEquals(3, scope.totalLessons(mateo.getId()));
        assertEquals(3, scope.completedLessons(mateo.getId()));
    }

    @Test
    void shouldMeasureEachParticipantAgainstTheirOwnVersionOnly() {
        User ana = user();
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));
        enroll(ana, v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS));
        done(mateo, v1, 2);
        done(ana, v2, 1);

        ContentScope scope = resolve(mateo, ana);

        assertEquals(List.of(v1), scope.versionsOf(mateo.getId()));
        assertEquals(List.of(v2), scope.versionsOf(ana.getId()));
        assertEquals(3, scope.totalLessons(mateo.getId()));
        assertEquals(2, scope.completedLessons(mateo.getId()));
        assertEquals(3, scope.totalLessons(ana.getId()));
        assertEquals(1, scope.completedLessons(ana.getId()));
        assertTrue(scope.isAssigned(mateo.getId(), v1.getId()));
        assertFalse(scope.isAssigned(mateo.getId(), v2.getId()));
        assertTrue(scope.versionIds().containsAll(List.of(v1.getId(), v2.getId())));
    }

    @Test
    void shouldMeasureSomeoneNotEnrolledAgainstThePublishedVersion() {
        contract(cafe);

        ContentScope scope = resolve(mateo);

        assertEquals(List.of(v2), scope.versionsOf(mateo.getId()));
        assertEquals(3, scope.totalLessons(mateo.getId()));
        assertEquals(0, scope.completedLessons(mateo.getId()));
    }

    @Test
    void shouldPreferTheEnrollmentWithMoreProgress() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));
        enroll(mateo, v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS));
        done(mateo, v1, 1);
        done(mateo, v2, 2);

        ContentScope scope = resolve(mateo);

        assertEquals(List.of(v2), scope.versionsOf(mateo.getId()));
        assertEquals(2, scope.completedLessons(mateo.getId()));
    }

    @Test
    void shouldPreferTheMostRecentEnrollmentOnATie() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));
        enroll(mateo, v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS));

        assertEquals(List.of(v2), resolve(mateo).versionsOf(mateo.getId()));
    }

    @Test
    void shouldPreferAnEnrollmentInForceOverADroppedOne() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.DROPPED, NOW.minus(5, ChronoUnit.DAYS));
        enroll(mateo, v2, EnrollmentStatus.ENROLLED, NOW.minus(1, ChronoUnit.HOURS));
        done(mateo, v1, 3);

        assertEquals(List.of(v2), resolve(mateo).versionsOf(mateo.getId()));
    }

    @Test
    void shouldKeepShowingADroppedEnrollmentWhenThereIsNothingElse() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.DROPPED, NOW.minus(5, ChronoUnit.DAYS));
        done(mateo, v1, 2);

        ContentScope scope = resolve(mateo);

        assertEquals(List.of(v1), scope.versionsOf(mateo.getId()));
        assertEquals(2, scope.completedLessons(mateo.getId()));
    }

    @Test
    void shouldIgnoreEnrollmentsStartedAfterTheEndOfThePeriod() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.plus(1, ChronoUnit.DAYS));

        assertEquals(List.of(v2), resolve(mateo).versionsOf(mateo.getId()));
    }

    @Test
    void shouldNeverCountMoreLessonsThanTheVersionHas() {
        contract(cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));
        done(mateo, v1, 5);

        assertEquals(3, resolve(mateo).completedLessons(mateo.getId()));
    }

    @Test
    void shouldUseTheNewestEnrolledVersionWhenNoneIsPublished() {
        contract(cafe);
        published.clear();
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));

        ContentScope scope = resolve(mateo);

        assertFalse(scope.isEmpty());
        assertEquals(v1, scope.courses().get(0).reference());
    }

    @Test
    void shouldAddUpEveryContractedCourseInContractOrder() {
        Course basics = Course.builder().id(UUID.randomUUID()).name("Básico").build();
        CourseVersion basicsV1 = version(basics, VersionStatus.PUBLISHED, NOW);
        published.add(basicsV1);
        lessonTotals.add(total(basicsV1.getId(), UUID.randomUUID(), 7));
        contract(basics, cafe);
        enroll(mateo, v1, EnrollmentStatus.ENROLLED, NOW.minus(5, ChronoUnit.DAYS));
        done(mateo, v1, 3);

        ContentScope scope = resolve(mateo);

        assertEquals(List.of(basicsV1, v1), scope.versionsOf(mateo.getId()));
        assertEquals(List.of(basics.getId(), cafe.getId()), scope.courseIds());
        assertEquals(10, scope.totalLessons(mateo.getId()));
        assertEquals(3, scope.completedLessons(mateo.getId()));
    }

    @Test
    void shouldNarrowTheScopeToTheRequestedCourse() {
        Course other = Course.builder().id(UUID.randomUUID()).build();
        contract(other, cafe);

        ContentScope scope = resolve(cafe.getId());

        assertEquals(List.of(cafe.getId()), scope.courseIds());
        assertEquals(v2, scope.courses().get(0).reference());
    }

    @Test
    void shouldRejectACourseTheOrganizationDidNotContract() {
        contract();

        assertThrows(
                NotFoundException.class,
                () -> contentScope.resolve(organizationId, UUID.randomUUID(), List.of(), NOW));
        verifyNoInteractions(courseVersionRepository);
    }
}
