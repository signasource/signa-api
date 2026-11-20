package com.signasource.signa_api.organizations.repository.projection;

import java.util.UUID;

/** People who had completed a topic, and people still on it, as of an instant. */
public interface TopicProgressCountView {

    UUID getTopicId();

    long getCompleted();

    long getInProgress();
}
