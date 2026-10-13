package com.signasource.signa_api.organizations.dto;

/**
 * How a participant is doing, as the panel labels them. "Active" uses the same window as the
 * dashboard overview, so filtering by it matches the overview's active count.
 */
public enum MemberActivityStatus {
    ACTIVE,
    INACTIVE,
    NOT_STARTED,
    COMPLETED,
    REMOVED
}
