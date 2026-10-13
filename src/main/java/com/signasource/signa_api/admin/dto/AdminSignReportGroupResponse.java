package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record AdminSignReportGroupResponse(
        UUID signId,
        String signMeaning,
        String signLanguage,
        String handedness,
        int reportCount,
        ReportReason topReason,
        ReportStatus status,
        LocalDateTime lastReportAt,
        List<ReportItem> reports) {

    public record ReportItem(
            UUID id,
            String username,
            ReportReason reason,
            String description,
            ReportStatus status,
            LocalDateTime createdAt) {}
}
