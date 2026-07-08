package com.signasource.signa_api.learning.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateSignReportRequest(
        @NotNull(message = "Sign ID is mandatory") UUID signId,
        @NotBlank(message = "The report reason is mandatory") String reason,
        String description) {}
