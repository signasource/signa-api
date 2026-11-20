package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.RefundRequestReason;
import jakarta.validation.constraints.Size;

/** Both fields are optional: the buyer may ask for a refund without giving a reason or comment. */
public record CreateRefundRequestRequest(
        RefundRequestReason reason, @Size(max = 2000) String message) {}
