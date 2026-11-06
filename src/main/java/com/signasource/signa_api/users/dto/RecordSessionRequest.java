package com.signasource.signa_api.users.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Instant;

/**
 * A client-reported app-usage session: when it started and how long it lasted. The server trusts
 * its own clock for {@code recordedAt} and bounds the duration (see {@code SessionService}).
 */
public record RecordSessionRequest(
        @NotNull Instant startedAt, @NotNull @Positive Integer durationSeconds) {}
