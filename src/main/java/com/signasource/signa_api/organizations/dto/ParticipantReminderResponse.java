package com.signasource.signa_api.organizations.dto;

import java.time.Instant;

/** A reminder just sent to a participant, and when the next one can be sent. */
public record ParticipantReminderResponse(
        Instant lastReminderAt, Instant nextReminderAvailableAt) {}
