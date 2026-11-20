package com.signasource.signa_api.gamification.dto;

/**
 * Pending gamification notifications related to the streak. Polled on app open and after lessons.
 *
 * @param pending {@code true} until {@code POST /users/me/streak-day/seen} — today added a streak
 *     day
 * @param currentStreak streak length (non-zero when {@code pending} or {@code shieldPending} is
 *     true)
 * @param shieldPending {@code true} until {@code POST /users/me/streak-shield/seen} — the nightly
 *     scheduler burned a shield to keep the streak alive
 */
public record StreakDayResponse(boolean pending, int currentStreak, boolean shieldPending) {}
