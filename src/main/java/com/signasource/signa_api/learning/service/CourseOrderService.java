package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.exceptions.PaymentProviderException;
import com.signasource.signa_api.gamification.service.GemOrderService;
import com.signasource.signa_api.learning.dto.CourseCheckoutResponse;
import com.signasource.signa_api.learning.dto.CourseOrderResponse;
import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseVersion;
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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buying permanent access to a paid course with real money through Mercado Pago Checkout Pro, one
 * payment. Modeled on {@link GemOrderService}: the app asks for a checkout URL and opens it, and
 * access is granted only after reading the order back from the Mercado Pago API with our token
 * ({@link CourseOrderSettlementService}), never from what the app or the notification says. Two
 * paths get there: the webhook (source of truth) and the app's sync when the buyer comes back. Not
 * transactional: it calls Mercado Pago over HTTP.
 */
@Service
public class CourseOrderService {

    private final CourseRepository courseRepository;
    private final CourseVersionRepository courseVersionRepository;
    private final CourseOrderRepository orderRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final CourseOrderSettlementService settlement;
    private final MercadoPagoClient mercadoPago;
    private final String baseUrl;

    public CourseOrderService(
            CourseRepository courseRepository,
            CourseVersionRepository courseVersionRepository,
            CourseOrderRepository orderRepository,
            UserCourseEnrollmentRepository enrollmentRepository,
            CourseOrderSettlementService settlement,
            MercadoPagoClient mercadoPago,
            @Value("${app.base-url:}") String baseUrl) {
        this.courseRepository = courseRepository;
        this.courseVersionRepository = courseVersionRepository;
        this.orderRepository = orderRepository;
        this.enrollmentRepository = enrollmentRepository;
        this.settlement = settlement;
        this.mercadoPago = mercadoPago;
        this.baseUrl = baseUrl;
    }

    @Transactional
    public CourseCheckoutResponse startCheckout(User user, UUID courseId) {
        Course course =
                courseRepository
                        .findById(courseId)
                        .orElseThrow(() -> new NotFoundException("Course not found"));
        if (course.isFree()) {
            throw new InvalidInputException("This course is free");
        }
        if (course.getPrice() == null || course.getPrice().signum() <= 0) {
            throw new InvalidInputException("This course has no price yet");
        }
        CourseVersion version =
                courseVersionRepository
                        .findByCourseIdAndStatus(courseId, VersionStatus.PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new NotFoundException(
                                                "Active published version not found for course"));
        if (hasAccess(user, version)) {
            throw new InvalidInputException("You already own this course");
        }
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new PaymentProviderException("app.base-url is required to return from checkout");
        }

        CourseOrder order =
                orderRepository.save(
                        CourseOrder.builder()
                                .user(user)
                                .course(course)
                                .amount(course.getPrice())
                                .currency("ARS")
                                .build());

        String returnUrl = baseUrl + GemOrderService.RETURN_PATH + "?status=";
        CheckoutOrder checkout =
                mercadoPago.createOrder(
                        new OrderRequest(
                                order.reference(),
                                "Curso " + course.getName() + " · Signa",
                                1,
                                course.getPrice(),
                                order.getCurrency(),
                                returnUrl + "approved",
                                returnUrl + "rejected",
                                returnUrl + "pending"));
        order.setMercadoPagoOrderId(checkout.id());
        orderRepository.save(order);
        return new CourseCheckoutResponse(order.getId(), checkout.checkoutUrl());
    }

    /** The app re-checks an order when the buyer comes back. Only the stored order is read. */
    public CourseOrderResponse sync(User user, UUID orderId) {
        CourseOrder order =
                orderRepository
                        .findByIdAndUserId(orderId, user.getId())
                        .orElseThrow(() -> new NotFoundException("Course order not found"));
        String mercadoPagoOrderId = order.getMercadoPagoOrderId();
        if (mercadoPagoOrderId == null || mercadoPagoOrderId.isBlank()) {
            throw new InvalidInputException("This order has no checkout to check yet");
        }
        Payment payment = mercadoPago.getOrder(mercadoPagoOrderId);
        CourseOrder settled = settlement.settle(order.getId(), payment);
        return CourseOrderResponse.from(settled != null ? settled : order);
    }

    /**
     * Applies a Mercado Pago order notified by the webhook; a no-op for what is not a course order.
     */
    public void applyPayment(Payment payment) {
        parseOrderId(payment.externalReference())
                .ifPresent(orderId -> settlement.settle(orderId, payment));
    }

    /** {@code true} when the Mercado Pago reference belongs to a course order. */
    public static boolean isCourseReference(String externalReference) {
        return externalReference != null
                && externalReference.startsWith(CourseOrder.REFERENCE_PREFIX);
    }

    private boolean hasAccess(User user, CourseVersion version) {
        return enrollmentRepository
                .findByUserIdAndCourseVersionId(user.getId(), version.getId())
                .filter(enrollment -> enrollment.grantsAccess(Instant.now()))
                .isPresent();
    }

    private static Optional<UUID> parseOrderId(String externalReference) {
        if (!isCourseReference(externalReference)) {
            return Optional.empty();
        }
        try {
            return Optional.of(
                    UUID.fromString(
                            externalReference.substring(CourseOrder.REFERENCE_PREFIX.length())));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
