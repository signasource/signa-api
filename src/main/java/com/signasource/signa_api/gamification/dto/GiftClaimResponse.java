package com.signasource.signa_api.gamification.dto;

public record GiftClaimResponse(
        GiftResponse gift, AppliedEffectResponse effect, UserInventoryResponse inventory) {}
