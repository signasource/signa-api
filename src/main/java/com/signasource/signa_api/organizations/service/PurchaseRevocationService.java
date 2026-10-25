package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.entity.RefundReason;
import com.signasource.signa_api.organizations.entity.SeatStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Undoes a paid purchase whose money was given back. Pending invitations are cancelled (their link
 * stops working) and the people who had accepted lose access to the course the way an expired
 * access does: the enrollment ends now and the progress stays readable. Seats already expired or
 * removed are left as they are. The purchase must already be locked by the caller.
 */
@Service
@RequiredArgsConstructor
public class PurchaseRevocationService {

    private final CoursePurchaseRepository purchaseRepository;
    private final PurchaseSeatRepository seatRepository;
    private final OrganizationEnrollmentService enrollmentService;
    private final PurchaseNotificationService notifications;

    /** What happened to the seats of a revoked purchase. */
    public record Revocation(List<String> cancelledInvitations, List<String> endedAccesses) {}

    @Transactional(propagation = Propagation.MANDATORY)
    public Revocation revoke(
            CoursePurchase purchase, RefundReason reason, BigDecimal refunded, Instant now) {
        purchase.setStatus(PurchaseStatus.REFUNDED);
        purchase.setRefundedAt(now);
        purchaseRepository.save(purchase);

        List<String> cancelled = new ArrayList<>();
        List<String> ended = new ArrayList<>();
        // Locked so an invitation being accepted right now finishes first and is seen as active.
        for (PurchaseSeat seat : seatRepository.findForUpdateByPurchaseId(purchase.getId())) {
            SeatStatus status = seat.statusAt(now);
            if (status == SeatStatus.INVITED || status == SeatStatus.PENDING_PAYMENT) {
                seat.setStatus(SeatStatus.CANCELLED);
                seat.setInvitationToken(null);
                seatRepository.save(seat);
                cancelled.add(seat.getEmail());
            } else if (status == SeatStatus.ACTIVE) {
                seat.setStatus(SeatStatus.REVOKED);
                seat.setRevokedAt(now);
                seatRepository.save(seat);
                if (seat.getUser() != null) {
                    enrollmentService.endCourseAccess(
                            seat.getUser(),
                            purchase.getOrganization().getId(),
                            purchase.getCourse().getId(),
                            now);
                }
                ended.add(seat.getEmail());
            }
        }

        Revocation revocation = new Revocation(List.copyOf(cancelled), List.copyOf(ended));
        notifications.purchaseRevoked(purchase, reason, refunded, revocation);
        return revocation;
    }
}
