package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.ReportReason;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateSignReportRequest(
        @NotNull(message = "Sign ID is mandatory") UUID signId,
        @NotNull(message = "The reason is mandatory") ReportReason reason,
        String description) {}
