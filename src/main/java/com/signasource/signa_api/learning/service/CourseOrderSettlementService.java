package com.signasource.signa_api.learning.service;

import com.signasource.signa_api.learning.entity.Course;
import com.signasource.signa_api.learning.entity.CourseOrder;
import com.signasource.signa_api.learning.entity.CourseOrderStatus;
import com.signasource.signa_api.learning.entity.CourseVersion;
import com.signasource.signa_api.learning.entity.EnrollmentStatus;
import com.signasource.signa_api.learning.entity.UserCourseEnrollment;
import com.signasource.signa_api.learning.entity.VersionStatus;
import com.signasource.signa_api.learning.repository.CourseOrderRepository;
import com.signasource.signa_api.learning.repository.CourseVersionRepository;
import com.signasource.signa_api.learning.repository.UserCourseEnrollmentRepository;
import com.signasource.signa_api.organizations.service.MercadoPagoClient.Payment;
import com.signasource.signa_api.users.entity.User;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The database half of paying a course order, split from {@link CourseOrderService} so the Mercado
 * Pago calls happen outside the transaction. The order row is locked, so the webhook and the app's
 * sync racing each other grant access once.
 *
 * <p>Paying grants a permanent self-enrollment ({@code organization = null}, {@code accessExpiresAt
 * = null}), mirroring {@link
 * com.signasource.signa_api.organizations.service.OrganizationEnrollmentService#grantCourse} but
 * without an expiry. {@link UserCourseEnrollment#grantsAccess(Instant)} stays the only access
 * check.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CourseOrderSettlementService {

    private final CourseOrderRepository orderRepository;
    private final UserCourseEnrollmentRepository enrollmentRepository;
    private final CourseVersionRepository courseVersionRepository;

    /** Applies what Mercado Pago says about the payment of an order. Returns the order, if any. */
    @Transactional
    public CourseOrder settle(UUID orderId, Payment payment) {
        CourseOrder order = orderRepository.findForUpdate(orderId).orElse(null);
        if (order == null) {
            log.warn("Mercado Pago payment {} matches no course order", payment.id());
            return null;
        }
        if (!payment.id().equals(order.getMercadoPagoOrderId())) {
            log.warn(
                    "Mercado Pago payment {} is not the one of course order {}",
                    payment.id(),
                    orderId);
            return order;
        }
        if (payment.isRefunded() || payment.isChargedBack()) {
            reverse(order);
        } else if (payment.isApproved() && order.getStatus() == CourseOrderStatus.PENDING) {
            grant(order, payment);
        }
        return order;
    }

    private void grant(CourseOrder order, Payment payment) {
        boolean sameAmount =
                payment.transactionAmount() != null
                        && payment.transactionAmount().compareTo(order.getAmount()) == 0;
        boolean sameCurrency = order.getCurrency().equalsIgnoreCase(payment.currency());
        if (!sameAmount || !sameCurrency) {
            log.error(
                    "Course order {} not granted: paid {} {} but costs {} {}",
                    order.getId(),
                    payment.transactionAmount(),
                    payment.currency(),
                    order.getAmount(),
                    order.getCurrency());
            return;
        }
        grantPermanentAccess(order.getUser(), order.getCourse());
        order.setStatus(CourseOrderStatus.PAID);
        order.setPaidAt(Instant.now());
        orderRepository.save(order);
    }

    /** The money went back: end the access this purchase granted, keeping the progress readable. */
    private void reverse(CourseOrder order) {
        if (order.getStatus() != CourseOrderStatus.PAID) {
            return;
        }
        Course course = order.getCourse();
        if (!course.isFree()) {
            courseVersionRepository
                    .findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED)
                    .flatMap(
                            version ->
                                    enrollmentRepository.findByUserIdAndCourseVersionId(
                                            order.getUser().getId(), version.getId()))
                    .filter(enrollment -> enrollment.getOrganization() == null)
                    .ifPresent(
                            enrollment -> {
                                enrollment.setStatus(EnrollmentStatus.DROPPED);
                                enrollmentRepository.save(enrollment);
                            });
        }
        order.setStatus(CourseOrderStatus.REFUNDED);
        order.setRefundedAt(Instant.now());
        orderRepository.save(order);
        log.warn("Course order {} refunded or charged back: access revoked", order.getId());
    }

    private void grantPermanentAccess(User user, Course course) {
        CourseVersion version =
                courseVersionRepository
                        .findByCourseIdAndStatus(course.getId(), VersionStatus.PUBLISHED)
                        .orElseThrow(
                                () ->
                                        new IllegalStateException(
                                                "Course "
                                                        + course.getId()
                                                        + " has no published version to grant"));

        UserCourseEnrollment enrollment =
                enrollmentRepository
                        .findByUserIdAndCourseVersionId(user.getId(), version.getId())
                        .orElseGet(
                                () ->
                                        UserCourseEnrollment.builder()
                                                .user(user)
                                                .courseVersion(version)
                                                .status(EnrollmentStatus.ENROLLED)
                                                .build());
        if (enrollment.getStatus() == EnrollmentStatus.DROPPED) {
            enrollment.setStatus(EnrollmentStatus.ENROLLED);
        }
        // A one-time individual purchase is permanent and not tied to any organization.
        enrollment.setOrganization(null);
        enrollment.setAccessExpiresAt(null);
        enrollmentRepository.save(enrollment);
    }
}
