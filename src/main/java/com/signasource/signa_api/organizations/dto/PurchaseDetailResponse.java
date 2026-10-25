package com.signasource.signa_api.organizations.dto;

import java.util.List;

public record PurchaseDetailResponse(PurchaseResponse purchase, List<SeatResponse> seats) {}
