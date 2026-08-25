package com.signasource.signa_api.learning.repository.projection;

import java.util.UUID;

public interface TopicCompletedCountView {

    UUID getTopicId();

    long getCompletedLessons();
}
