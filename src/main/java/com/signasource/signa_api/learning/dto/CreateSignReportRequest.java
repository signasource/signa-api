package com.signasource.signa_api.learning.dto;

import com.signasource.signa_api.learning.entity.ReportReason;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSignReportRequest(
        @NotBlank(message = "Sign meaning is mandatory") String signMeaning,
        @NotNull(message = "The reason is mandatory") ReportReason reason,
        String description) {}
