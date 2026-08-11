package com.signasource.signa_api.learning.repository.projection;

import java.util.UUID;

/** Number of lessons a user has completed within a topic. */
public interface TopicCompletedCountView {

    UUID getTopicId();

    long getCompletedLessons();
}
