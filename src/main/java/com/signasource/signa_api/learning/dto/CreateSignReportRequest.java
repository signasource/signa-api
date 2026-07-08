package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateSignReportRequest(
    @NotNull(message = "Sign ID is mandatory")
    Long signId,

    @NotBlank(message = "The report reason is mandatory")
    String reason,

    String description
) {}
