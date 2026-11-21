package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import java.time.Instant;
import java.util.UUID;

/**
 * A sent invitation (accepted or not yet) with the purchase it belongs to, for the panel's list.
 * {@code invitationDeadline} is the purchase's: after it the invitation can no longer be accepted.
 */
public record OrganizationInvitationResponse(
        UUID purchaseId,
        UUID courseId,
        String courseName,
        PurchaseStatus purchaseStatus,
        Instant invitationDeadline,
        SeatResponse seat) {

    public static OrganizationInvitationResponse of(CoursePurchase purchase, SeatResponse seat) {
        return new OrganizationInvitationResponse(
                purchase.getId(),
                purchase.getCourse().getId(),
                purchase.getCourse().getName(),
                purchase.getStatus(),
                purchase.getInvitationDeadline(),
                seat);
    }
}
