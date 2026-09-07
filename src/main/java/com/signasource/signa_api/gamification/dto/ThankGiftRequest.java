package com.signasource.signa_api.gamification.dto;

import jakarta.validation.constraints.Size;

public record ThankGiftRequest(@Size(max = 500) String message) {}
