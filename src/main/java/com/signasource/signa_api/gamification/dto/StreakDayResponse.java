package com.signasource.signa_api.gamification.dto;

/**
 * Whether today added a day to the user's streak that the app has not celebrated yet.
 *
 * @param pending {@code true} until {@code POST /users/me/streak-day/seen}
 * @param currentStreak streak length including today (0 when nothing is pending)
 */
public record StreakDayResponse(boolean pending, int currentStreak) {}
