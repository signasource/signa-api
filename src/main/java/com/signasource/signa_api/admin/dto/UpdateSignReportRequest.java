package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.ReportStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateSignReportRequest(@NotNull ReportStatus status, String adminNote) {}
