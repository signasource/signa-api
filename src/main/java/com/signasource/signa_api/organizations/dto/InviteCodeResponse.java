package com.signasource.signa_api.organizations.dto;

import com.signasource.signa_api.organizations.entity.InviteCode;
import java.time.Instant;
import java.util.UUID;

public record InviteCodeResponse(
        UUID id,
        String code,
        UUID organizationId,
        String email,
        Instant expiresAt,
        Integer maxUses,
        int useCount,
        boolean active) {
    public static InviteCodeResponse from(InviteCode inviteCode) {
        return new InviteCodeResponse(
                inviteCode.getId(),
                inviteCode.getCode(),
                inviteCode.getOrganization().getId(),
                inviteCode.getEmail(),
                inviteCode.getExpiresAt(),
                inviteCode.getMaxUses(),
                inviteCode.getUseCount(),
                inviteCode.isActive());
    }
}
