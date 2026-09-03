package com.signasource.signa_api.users.dto;

/**
 * Relationship between the authenticated user and another user, from the caller's point of view.
 */
public enum RelationStatus {
    /** No relationship, or a previously rejected request (a new one can be sent). */
    NONE,
    FRIEND,
    /** The other user sent the caller a request that is still pending. */
    INCOMING,
    /** The caller sent the other user a request that is still pending. */
    OUTGOING,
    /** The caller blocked the other user. */
    BLOCKED,
    /** The other user blocked the caller. */
    BLOCKED_BY
}
