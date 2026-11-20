package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.learning.entity.ReportReason;
import com.signasource.signa_api.learning.entity.ReportStatus;
import com.signasource.signa_api.learning.entity.SignReport;
import java.time.LocalDateTime;
import java.util.UUID;

public record AdminSignReportResponse(
        UUID id,
        UUID signId,
        String signMeaning,
        String signLanguage,
        String handedness,
        String username,
        ReportReason reason,
        String description,
        ReportStatus status,
        String adminNote,
        String resolvedBy,
        LocalDateTime resolvedAt,
        LocalDateTime createdAt) {

    public static AdminSignReportResponse from(SignReport r) {
        String resolvedByName = null;
        if (r.getResolvedBy() != null) {
            resolvedByName = r.getResolvedBy().getName() + " " + r.getResolvedBy().getLastName();
        }
        return new AdminSignReportResponse(
                r.getId(),
                r.getSign().getId(),
                r.getSign().getMeaning(),
                r.getSign().getSignLanguage().getName(),
                r.getSign().getHandedness().name(),
                r.getUser().getUsername(),
                r.getReason(),
                r.getDescription(),
                r.getStatus(),
                r.getAdminNote(),
                resolvedByName,
                r.getResolvedAt(),
                r.getCreatedAt());
    }
}
