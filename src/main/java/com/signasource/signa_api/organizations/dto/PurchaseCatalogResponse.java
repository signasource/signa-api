package com.signasource.signa_api.organizations.dto;

import java.math.BigDecimal;
import java.util.List;

public record PurchaseCatalogResponse(
        List<CatalogCourseResponse> courses,
        String currency,
        BigDecimal pricePerSignPerDay,
        BigDecimal minPricePerSeat,
        int minDays,
        int maxDays,
        int invitationDays,
        int maxSeats) {}
