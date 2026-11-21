package com.signasource.signa_api.organizations.dto;

/**
 * Invitations of the organization's paid purchases (refunded ones never count): how many were sent,
 * how many people accepted, and how many can still be accepted.
 *
 * @param acceptancePercentage {@code accepted / sent}, rounded; 0 when nothing was sent
 */
public record InvitationSummaryResponse(
        long sent, long accepted, long pending, int acceptancePercentage) {}
