package com.signasource.signa_api.gamification.dto;

import java.time.LocalDate;

public record DailyXpResponse(LocalDate date, int xpEarned) {}
