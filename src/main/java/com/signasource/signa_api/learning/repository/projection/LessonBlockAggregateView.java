package com.signasource.signa_api.learning.repository.projection;

import java.util.UUID;

public interface LessonBlockAggregateView {

    UUID getLessonId();

    long getBlockCount();

    long getXpTotal();
}
