package com.signasource.signa_api.organizations.repository.projection;

import java.util.UUID;

/** A user's state on one topic: {@code completed} is 1 when done, 0 when still in progress. */
public interface UserTopicStateView {

    UUID getUserId();

    UUID getTopicId();

    long getCompleted();
}
