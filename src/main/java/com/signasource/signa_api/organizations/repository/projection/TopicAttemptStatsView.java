package com.signasource.signa_api.organizations.repository.projection;

import java.util.UUID;

public interface TopicAttemptStatsView {

    UUID getTopicId();

    long getEvaluated();

    long getCorrect();
}
