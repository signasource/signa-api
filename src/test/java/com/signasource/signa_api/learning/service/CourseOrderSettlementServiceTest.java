package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.TopicRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.learning.repository.UserLessonProgressRepository;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseOrderSettlementServiceTest {

    @Mock private CourseOrderRepository orderRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private CourseVersionRepository courseVersionRepository;

    private CourseOrderSettlementService service;
    private CourseOrder order;
    private User user;
    private Course course;
    private CourseVersion version;
    private UserCourseEnrollment enrollment;

    @BeforeEach
    void setUp() {
        service =
                new CourseOrderSettlementService(
                        orderRepository,
                        enrollmentRepository,
                        courseVersionRepository,
                        new CourseEnrollmentResolver(
                                enrollmentRepository,
                                mock(UserLessonProgressRepository.class),
                                mock(TopicRepository.class)));
        user = User.builder().id(UUID.randomUUID()).build();
        course = Course.builder().id(UUID.randomUUID()).name("LSA Básico").isFree(false).build();
        version =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .course(course)
                        .status(VersionStatus.PUBLISHED)
                        .build();
        enrollment =
                UserCourseEnrollment.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .courseVersion(version)
                        .status(EnrollmentStatus.ENROLLED)
                        .build();
        order =
                CourseOrder.builder()
                        .id(UUID.randomUUID())
                        .user(user)
                        .course(course)
                        .amount(BigDecimal.valueOf(2000))
                        .currency("ARS")
                        .status(CourseOrderStatus.PAID)
                        .build();
    }

    @Test
    void shouldDropEnrollmentAndMarkRefundedWhenReversedManually() {
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(enrollmentRepository.findByUserIdAndCourseVersionCourseId(
                        user.getId(), course.getId()))
                .thenReturn(List.of(enrollment));

        service.reverseManual(order.getId());

        assertEquals(CourseOrderStatus.REFUNDED, order.getStatus());
        assertNotNull(order.getRefundedAt());
        assertEquals(EnrollmentStatus.DROPPED, enrollment.getStatus());
        verify(enrollmentRepository).save(enrollment);
        verify(orderRepository).save(order);
    }

    @Test
    void shouldDropTheEnrollmentEvenWhenItIsInAnOlderVersion() {
        CourseVersion archived =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .course(course)
                        .status(VersionStatus.ARCHIVED)
                        .build();
        enrollment.setCourseVersion(archived);
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(enrollmentRepository.findByUserIdAndCourseVersionCourseId(
                        user.getId(), course.getId()))
                .thenReturn(List.of(enrollment));

        service.reverseManual(order.getId());

        assertEquals(EnrollmentStatus.DROPPED, enrollment.getStatus());
    }

    @Test
    void shouldNotAllowManualRefundOfNonPaidOrder() {
        order.setStatus(CourseOrderStatus.PENDING);
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        assertThrows(InvalidInputException.class, () -> service.reverseManual(order.getId()));
        verify(orderRepository, never()).save(any());
    }

    @Test
    void shouldThrowNotFoundWhenOrderDoesNotExistForManualRefund() {
        UUID unknown = UUID.randomUUID();
        when(orderRepository.findForUpdate(unknown)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.reverseManual(unknown));
    }
}
