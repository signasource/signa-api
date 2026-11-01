package com.signasource.signa_api.organizations.dto;

import java.util.List;

public record PurchaseCatalogResponse(
        List<CatalogCourseResponse> courses,
        String currency,
        List<VolumeDiscountResponse> volumeDiscounts,
        int minDays,
        int maxDays,
        int invitationDays,
        int maxSeats) {}
