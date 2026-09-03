package com.signasource.signa_api.notification.dto;

/** How many notifications the user has not opened yet — drives the bell badge. */
public record UnreadCountResponse(long unreadCount) {}
