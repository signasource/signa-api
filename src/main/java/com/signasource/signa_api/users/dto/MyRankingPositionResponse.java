package com.signasource.signa_api.users.dto;

public record MyRankingPositionResponse(
        int rank,
        int weeklyXp,
        /** Positive = moved up; negative = moved down; null = no previous data. */
        Integer delta,
        /**
         * Pre-formatted gap text in Spanish, e.g. "Te faltan 320 XP para entrar al top 10". Null
         * when rank is 1.
         */
        String gapText) {}
