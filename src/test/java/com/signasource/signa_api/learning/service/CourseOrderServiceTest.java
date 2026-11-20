package com.signasource.signa_api.learning.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.learning.dto.CourseCheckoutResponse;
import com.signasource.signa_api.learning.dto.CourseOrderResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.repository.CourseRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.CheckoutOrder;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.OrderRequest;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.users.entity.User;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CourseOrderServiceTest {

    private static final String MP_ORDER = "ORD123";
    private static final String BASE_URL = "https://api.signa.test";

    @Mock private CourseRepository courseRepository;
    @Mock private CourseVersionRepository courseVersionRepository;
    @Mock private CourseOrderRepository orderRepository;
    @Mock private UserCourseEnrollmentRepository enrollmentRepository;
    @Mock private MercadoPagoClient mercadoPago;

    private CourseOrderService service;
    private User user;
    private Course course;
    private CourseVersion version;

    @BeforeEach
    void setUp() {
        user = User.builder().id(UUID.randomUUID()).build();
        course =
                Course.builder()
                        .id(UUID.randomUUID())
                        .code("paid-course")
                        .name("Curso Pago")
                        .isFree(false)
                        .price(new BigDecimal("2900"))
                        .build();
        version =
                CourseVersion.builder()
                        .id(UUID.randomUUID())
                        .version("1.0")
                        .status(VersionStatus.PUBLISHED)
                        .course(course)
                        .build();

        CourseOrderSettlementService settlement =
                new CourseOrderSettlementService(
                        orderRepository, enrollmentRepository, courseVersionRepository);
        service =
                new CourseOrderService(
                        courseRepository,
                        courseVersionRepository,
                        orderRepository,
                        enrollmentRepository,
                        settlement,
                        mercadoPago,
                        BASE_URL);

        lenient()
                .when(orderRepository.save(any(CourseOrder.class)))
                .thenAnswer(
                        invocation -> {
                            CourseOrder order = invocation.getArgument(0);
                            if (order.getId() == null) {
                                order.setId(UUID.randomUUID());
                            }
                            return order;
                        });
    }

    @Test
    void startCheckoutFreezesThePriceAndReturnsTheCheckoutUrl() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.empty());
        when(mercadoPago.createOrder(any()))
                .thenReturn(new CheckoutOrder(MP_ORDER, "https://mp.test/checkout"));

        CourseCheckoutResponse response = service.startCheckout(user, course.getId());

        assertEquals("https://mp.test/checkout", response.checkoutUrl());
        ArgumentCaptor<OrderRequest> request = ArgumentCaptor.forClass(OrderRequest.class);
        verify(mercadoPago).createOrder(request.capture());
        assertEquals("course_" + response.orderId(), request.getValue().externalReference());
        assertEquals(1, request.getValue().quantity());
        assertEquals(new BigDecimal("2900"), request.getValue().unitPrice());
        assertEquals("ARS", request.getValue().currency());
        assertTrue(request.getValue().successUrl().startsWith(BASE_URL));
    }

    @Test
    void startCheckoutRejectsAFreeCourse() {
        course.setFree(true);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThrows(
                InvalidInputException.class, () -> service.startCheckout(user, course.getId()));
        verify(mercadoPago, never()).createOrder(any());
    }

    @Test
    void startCheckoutRejectsACourseWithoutPrice() {
        course.setPrice(null);
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));

        assertThrows(
                InvalidInputException.class, () -> service.startCheckout(user, course.getId()));
        verify(mercadoPago, never()).createOrder(any());
    }

    @Test
    void startCheckoutRejectsWhenTheUserAlreadyOwnsTheCourse() {
        when(courseRepository.findById(course.getId())).thenReturn(Optional.of(course));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.of(enrollment(EnrollmentStatus.ENROLLED)));

        assertThrows(
                InvalidInputException.class, () -> service.startCheckout(user, course.getId()));
        verify(mercadoPago, never()).createOrder(any());
    }

    @Test
    void startCheckoutFailsForUnknownCourse() {
        UUID id = UUID.randomUUID();
        when(courseRepository.findById(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.startCheckout(user, id));
    }

    @Test
    void syncGrantsPermanentAccessOnceWhenApproved() {
        CourseOrder order = pendingOrder();
        stubOrder(order);
        stubGrant();
        when(mercadoPago.getOrder(MP_ORDER)).thenReturn(approved(order, "2900"));

        CourseOrderResponse first = service.sync(user, order.getId());
        CourseOrderResponse second = service.sync(user, order.getId());

        assertEquals(CourseOrderStatus.PAID, first.status());
        assertTrue(first.owned());
        assertEquals(CourseOrderStatus.PAID, second.status());

        ArgumentCaptor<UserCourseEnrollment> saved =
                ArgumentCaptor.forClass(UserCourseEnrollment.class);
        verify(enrollmentRepository).save(saved.capture());
        assertEquals(EnrollmentStatus.ENROLLED, saved.getValue().getStatus());
        assertNull(saved.getValue().getOrganization());
        assertNull(saved.getValue().getAccessExpiresAt());
    }

    @Test
    void syncDoesNotGrantWhileThePaymentIsNotApproved() {
        CourseOrder order = pendingOrder();
        stubOrder(order);
        when(mercadoPago.getOrder(MP_ORDER))
                .thenReturn(new Payment(MP_ORDER, "pending", "", order.reference(), null, "ARS"));

        CourseOrderResponse response = service.sync(user, order.getId());

        assertEquals(CourseOrderStatus.PENDING, response.status());
        assertFalse(response.owned());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void syncDoesNotGrantAWrongAmount() {
        CourseOrder order = pendingOrder();
        stubOrder(order);
        when(mercadoPago.getOrder(MP_ORDER)).thenReturn(approved(order, "10"));

        CourseOrderResponse response = service.sync(user, order.getId());

        assertEquals(CourseOrderStatus.PENDING, response.status());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void syncRejectsAnOrderOfAnotherUser() {
        UUID orderId = UUID.randomUUID();
        when(orderRepository.findByIdAndUserId(orderId, user.getId())).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.sync(user, orderId));
    }

    @Test
    void webhookPaymentGrantsTheOrderItReferences() {
        CourseOrder order = pendingOrder();
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        stubGrant();

        service.applyPayment(approved(order, "2900"));

        assertEquals(CourseOrderStatus.PAID, order.getStatus());
        verify(enrollmentRepository).save(any());
    }

    @Test
    void aRefundRevokesTheAccessThePurchaseGranted() {
        CourseOrder order = pendingOrder();
        order.setStatus(CourseOrderStatus.PAID);
        UserCourseEnrollment granted = enrollment(EnrollmentStatus.ENROLLED);
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
        when(courseVersionRepository.findByCourseIdAndStatus(
                        course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        when(enrollmentRepository.findByUserIdAndCourseVersionId(user.getId(), version.getId()))
                .thenReturn(Optional.of(granted));

        service.applyPayment(
                new Payment(
                        MP_ORDER,
                        "refunded",
                        "",
                        order.reference(),
                        new BigDecimal("2900"),
                        "ARS"));

        assertEquals(CourseOrderStatus.REFUNDED, order.getStatus());
        assertEquals(EnrollmentStatus.DROPPED, granted.getStatus());
    }

    @Test
    void aPaymentOfAnotherMercadoPagoOrderIsIgnored() {
        CourseOrder order = pendingOrder();
        when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));

        service.applyPayment(
                new Payment(
                        "OTHER", "approved", "", order.reference(), new BigDecimal("2900"), "ARS"));

        assertEquals(CourseOrderStatus.PENDING, order.getStatus());
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    void courseReferencesAreToldApartFromOthers() {
        assertTrue(CourseOrderService.isCourseReference("course_" + UUID.randomUUID()));
        assertFalse(CourseOrderService.isCourseReference("gems_" + UUID.randomUUID()));
        assertFalse(CourseOrderService.isCourseReference(null));
    }

    private CourseOrder pendingOrder() {
        return CourseOrder.builder()
                .id(UUID.randomUUID())
                .user(user)
                .course(course)
                .amount(new BigDecimal("2900.00"))
                .currency("ARS")
                .mercadoPagoOrderId(MP_ORDER)
                .build();
    }

    private UserCourseEnrollment enrollment(EnrollmentStatus status) {
        return UserCourseEnrollment.builder()
                .id(UUID.randomUUID())
                .user(user)
                .courseVersion(version)
                .status(status)
                .build();
    }

    private void stubOrder(CourseOrder order) {
        when(orderRepository.findByIdAndUserId(order.getId(), user.getId()))
                .thenReturn(Optional.of(order));
        lenient().when(orderRepository.findForUpdate(order.getId())).thenReturn(Optional.of(order));
    }

    private void stubGrant() {
        lenient()
                .when(
                        courseVersionRepository.findByCourseIdAndStatus(
                                course.getId(), VersionStatus.PUBLISHED))
                .thenReturn(Optional.of(version));
        lenient()
                .when(
                        enrollmentRepository.findByUserIdAndCourseVersionId(
                                user.getId(), version.getId()))
                .thenReturn(Optional.empty());
    }

    private Payment approved(CourseOrder order, String amount) {
        return new Payment(
                MP_ORDER, "approved", "", order.reference(), new BigDecimal(amount), "ARS");
    }
}
