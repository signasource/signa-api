package com.signasource.signa_api.users.dto;

import java.util.UUID;

public record RankingEntryResponse(
        int rank,
        UUID id,
        String username,
        String name,
        int weeklyXp,
        int currentStreak,
        /** Positive = moved up; negative = moved down; null = no previous data. */
        Integer delta) {}
