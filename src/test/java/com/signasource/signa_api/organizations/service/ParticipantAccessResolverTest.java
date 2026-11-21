package com.signasource.signa_api.organizations.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.Organization;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import com.signasource.signa_api.users.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ParticipantAccessResolverTest {

    @Mock private PurchaseSeatRepository seatRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;

    @InjectMocks private ParticipantAccessResolver resolver;

    private final Instant now = Instant.parse("2026-11-20T15:00:00Z");
    private final Organization organization = Organization.builder().id(UUID.randomUUID()).build();
    private final Course salud = Course.builder().id(UUID.randomUUID()).name("Salud").build();
    private final Course cafe = Course.builder().id(UUID.randomUUID()).name("Café").build();
    private final List<PurchaseSeat> seats = new ArrayList<>();
    private final List<UserCourseEnrollment> enrollments = new ArrayList<>();
    private User ana;

    @BeforeEach
    void setUp() {
        ana = new User();
        ana.setId(UUID.randomUUID());
    }

    private void seat(Course course, PurchaseStatus purchaseStatus, SeatStatus status) {
        seats.add(
                PurchaseSeat.builder()
                        .purchase(
                                CoursePurchase.builder()
                                        .organization(organization)
                                        .course(course)
                                        .status(purchaseStatus)
                                        .build())
                        .user(ana)
                        .email("ana@x.com")
                        .status(status)
                        .activatedAt(now.minus(Duration.ofDays(5)))
                        .accessExpiresAt(now.plus(Duration.ofDays(25)))
                        .build());
    }

    private void enroll(Course course, EnrollmentStatus status) {
        enrollments.add(
                UserCourseEnrollment.builder()
                        .user(ana)
                        .courseVersion(
                                CourseVersion.builder()
                                        .id(UUID.randomUUID())
                                        .course(course)
                                        .build())
                        .organization(organization)
                        .status(status)
                        .startedAt(now.minus(Duration.ofDays(60)))
                        .build());
    }

    private Map<UUID, Map<UUID, CourseAccess>> resolve() {
        when(seatRepository.findAcceptedByOrganizationIdAndUserIdIn(
                        eq(organization.getId()), anyCollection()))
                .thenReturn(seats);
        when(enrollmentRepository.findOrganizationEnrollments(
                        eq(organization.getId()), anyCollection(), anyCollection()))
                .thenReturn(enrollments);
        return resolver.resolve(
                organization.getId(), List.of(ana.getId()), List.of(salud.getId(), cafe.getId()));
    }

    @Test
    void shouldGrantTheCourseOfAPaidSeat() {
        seat(salud, PurchaseStatus.PAID, SeatStatus.ACTIVE);
        enroll(salud, EnrollmentStatus.ENROLLED);

        CourseAccess access = resolve().get(ana.getId()).get(salud.getId());

        assertEquals(now.minus(Duration.ofDays(5)), access.startedAt());
        assertEquals(now.plus(Duration.ofDays(25)), access.expiresAt());
        assertTrue(access.isRunning(now));
    }

    @Test
    void shouldNotGrantACourseWhoseOnlySeatWasRefunded() {
        seat(salud, PurchaseStatus.REFUNDED, SeatStatus.REVOKED);
        enroll(salud, EnrollmentStatus.ENROLLED);

        assertTrue(resolve().isEmpty());
    }

    @Test
    void shouldKeepOtherCoursesWhenOneWasRefunded() {
        seat(salud, PurchaseStatus.REFUNDED, SeatStatus.REVOKED);
        seat(cafe, PurchaseStatus.PAID, SeatStatus.ACTIVE);
        enroll(salud, EnrollmentStatus.ENROLLED);
        enroll(cafe, EnrollmentStatus.ENROLLED);

        assertEquals(List.of(cafe.getId()), List.copyOf(resolve().get(ana.getId()).keySet()));
    }

    @Test
    void shouldKeepTheCourseOfSomeoneRemovedButNotRunIt() {
        seat(salud, PurchaseStatus.PAID, SeatStatus.REVOKED);
        enroll(salud, EnrollmentStatus.DROPPED);

        CourseAccess access = resolve().get(ana.getId()).get(salud.getId());

        assertTrue(access.revoked());
        assertFalse(access.isRunning(now));
    }

    @Test
    void shouldGrantByEnrollmentWhenThereWasNeverASeat() {
        enroll(cafe, EnrollmentStatus.ENROLLED);

        CourseAccess access = resolve().get(ana.getId()).get(cafe.getId());

        assertEquals(now.minus(Duration.ofDays(60)), access.startedAt());
        assertNull(access.expiresAt());
        assertFalse(access.revoked());
    }

    @Test
    void shouldResolveNothingWithoutUsers() {
        assertTrue(
                resolver.resolve(organization.getId(), List.of(), List.of(salud.getId()))
                        .isEmpty());
        verifyNoInteractions(seatRepository, enrollmentRepository);
    }
}
