package com.signasource.signa_api.admin.dto;

import jakarta.validation.constraints.Size;

public record UpdateRefundRequestRequest(
        String action,
        @Size(max = 2000) String note) {}
