package com.signasource.signa_api.admin.dto;

import com.signasource.signa_api.waitlist.entity.WaitlistEntry;
import java.time.Instant;

public record AdminWaitlistEntryResponse(Long id, String email, Instant createdAt) {
    public static AdminWaitlistEntryResponse from(WaitlistEntry entry) {
        return new AdminWaitlistEntryResponse(
                entry.getId(), entry.getEmail(), entry.getCreatedAt());
    }
}
