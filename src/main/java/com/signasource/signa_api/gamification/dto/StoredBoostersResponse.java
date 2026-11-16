package com.signasource.signa_api.gamification.dto;

/** Boosters bought but not yet activated, by type. */
public record StoredBoostersResponse(long xpMultiplier, long unlimitedLives) {}
