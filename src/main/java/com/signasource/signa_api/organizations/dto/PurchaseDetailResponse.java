package com.signasource.signa_api.organizations.dto;

import java.util.List;

/** A purchase, its seats and the payments given back for it (usually none). */
public record PurchaseDetailResponse(
        PurchaseResponse purchase,
        List<SeatResponse> seats,
        List<PurchaseRefundResponse> refunds) {}
