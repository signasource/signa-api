package com.signasource.signa_api.organizations.dto;

public record RefundEligibilityResponse(boolean eligible, String reason) {

    public static RefundEligibilityResponse ok() {
        return new RefundEligibilityResponse(true, null);
    }

    public static RefundEligibilityResponse no(String reason) {
        return new RefundEligibilityResponse(false, reason);
    }
}
