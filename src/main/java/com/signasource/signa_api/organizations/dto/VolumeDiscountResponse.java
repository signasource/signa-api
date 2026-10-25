package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.service.PurchasePricing.VolumeDiscount;

public record VolumeDiscountResponse(int minSeats, int percent) {

    public static VolumeDiscountResponse from(VolumeDiscount discount) {
        return new VolumeDiscountResponse(discount.minSeats(), discount.percent());
    }
}
