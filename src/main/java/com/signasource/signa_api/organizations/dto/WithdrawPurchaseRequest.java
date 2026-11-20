package com.signasource.signa_api.organizations.dto;

import jakarta.validation.constraints.Size;

/** Optional reason the organization gives when it cancels a paid purchase. */
public record WithdrawPurchaseRequest(@Size(max = 500) String reason) {}
