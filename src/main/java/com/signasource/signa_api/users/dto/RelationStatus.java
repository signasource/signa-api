package com.signasource.signa_api.users.dto;

/** From the caller's point of view: {@code OUTGOING} and {@code BLOCKED} are the caller's doing. */
public enum RelationStatus {
    /** Also a rejected request: a new one can be sent. */
    NONE,
    FRIEND,
    INCOMING,
    OUTGOING,
    BLOCKED,
    BLOCKED_BY
}
