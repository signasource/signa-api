package com.signasource.signa_api.organizations.repository.projection;

import java.time.LocalDate;

public interface DailyMinutesView {
    LocalDate getDay();

    long getMinutes();
}
