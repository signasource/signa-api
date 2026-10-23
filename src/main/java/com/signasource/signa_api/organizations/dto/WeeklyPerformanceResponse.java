package com.signasource.signa_api.organizations.dto;

import java.time.LocalDate;

public record WeeklyPerformanceResponse(
        LocalDate weekStart, long exerciseAttempts, int correctPercentage) {}
