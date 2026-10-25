package com.signasource.signa_api.organizations.service;

import com.signasource.signa_api.admin.dto.AdminPurchaseResponse;
import com.signasource.signa_api.exceptions.InvalidInputException;
import com.signasource.signa_api.exceptions.NotFoundException;
import com.signasource.signa_api.organizations.dto.PurchaseDetailResponse;
import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import com.signasource.signa_api.organizations.entity.CoursePurchase;
import com.signasource.signa_api.organizations.entity.OrganizationCourse;
import com.signasource.signa_api.organizations.entity.PurchaseSeat;
import com.signasource.signa_api.organizations.entity.PurchaseStatus;
import com.signasource.signa_api.organizations.repository.CoursePurchaseRepository;
import com.signasource.signa_api.organizations.repository.OrganizationCourseRepository;
import com.signasource.signa_api.organizations.repository.PurchaseSeatRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PurchasePaymentService {

    private final CoursePurchaseRepository purchaseRepository;
    private final PurchaseSeatRepository seatRepository;
    private final OrganizationCourseRepository organizationCourseRepository;
    private final SeatInvitationService invitationService;
    private final CoursePurchaseService purchaseService;
    private final PurchasePolicy policy;

    @Transactional
    public PurchaseDetailResponse confirmPayment(UUID purchaseId, String paymentReference) {
        CoursePurchase purchase =
                purchaseRepository
                        .findForUpdateById(purchaseId)
                        .orElseThrow(() -> new NotFoundException("Purchase not found"));
        if (purchase.getStatus() == PurchaseStatus.PAID) {
            return purchaseService.describe(purchase);
        }
        if (purchase.getStatus() == PurchaseStatus.CANCELLED) {
            throw new InvalidInputException("Purchase was cancelled");
        }

        Instant now = Instant.now();
        purchase.setStatus(PurchaseStatus.PAID);
        purchase.setPaymentReference(paymentReference);
        purchase.setPaidAt(now);
        purchase.setInvitationDeadline(now.plus(policy.invitationWindow()));
        purchaseRepository.save(purchase);

        UUID organizationId = purchase.getOrganization().getId();
        UUID courseId = purchase.getCourse().getId();
        if (!organizationCourseRepository.existsByOrganizationIdAndCourseId(
                organizationId, courseId)) {
            organizationCourseRepository.save(
                    OrganizationCourse.builder()
                            .organization(purchase.getOrganization())
                            .course(purchase.getCourse())
                            .build());
        }

        for (PurchaseSeat seat : seatRepository.findByPurchaseIdOrderByEmailAsc(purchaseId)) {
            invitationService.invite(seat, now);
        }
        return purchaseService.describe(purchase);
    }

    @Transactional(readOnly = true)
    public List<AdminPurchaseResponse> listForAdmin(PurchaseStatus status) {
        List<CoursePurchase> purchases =
                status == null
                        ? purchaseRepository.findAllByOrderByCreatedAtDesc()
                        : purchaseRepository.findByStatusOrderByCreatedAtAsc(status);
        Instant now = Instant.now();
        return purchases.stream()
                .map(
                        purchase ->
                                new AdminPurchaseResponse(
                                        purchase.getOrganization().getId(),
                                        purchase.getOrganization().getName(),
                                        purchase.getPaymentReference(),
                                        PurchaseResponse.from(
                                                purchase,
                                                seatRepository.findByPurchaseIdOrderByEmailAsc(
                                                        purchase.getId()),
                                                now)))
                .toList();
    }
}
