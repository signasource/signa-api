package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.organizations.dto.PurchaseResponse;
import java.util.UUID;

public record AdminPurchaseResponse(
        UUID organizationId,
        String organizationName,
        String paymentReference,
        PurchaseResponse purchase) {}
