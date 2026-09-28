package com.signasource.signa_api.organizations.repository.projection;

import java.time.LocalDate;

public interface DailyAttemptStatsView {

    LocalDate getDay();

    long getEvaluated();

    long getCorrect();
}
