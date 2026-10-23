package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.content.entity.ContentImportRun;
import com.signasource.signa_api.content.entity.ContentImportStatus;
import com.signasource.signa_api.content.entity.ContentImportTrigger;
import java.time.Instant;
import java.util.UUID;

public record ContentImportRunResponse(
        UUID id,
        Instant startedAt,
        Instant finishedAt,
        ContentImportTrigger trigger,
        ContentImportStatus status,
        String triggeredBy,
        int coursesProcessed,
        int created,
        int updated,
        int unchanged,
        String details) {

    public static ContentImportRunResponse from(ContentImportRun run) {
        return new ContentImportRunResponse(
                run.getId(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getTrigger(),
                run.getStatus(),
                run.getTriggeredBy(),
                run.getCoursesProcessed(),
                run.getCreated(),
                run.getUpdated(),
                run.getUnchanged(),
                run.getDetails());
    }
}
